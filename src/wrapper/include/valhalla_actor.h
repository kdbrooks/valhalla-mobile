#ifndef VALHALLAACTOR_H
#define VALHALLAACTOR_H

#include <atomic>
#include <chrono>
#include <string>
#include <valhalla/tyr/actor.h>
#include <valhalla/baldr/tilegetter.h>

class ValhallaMobileHttpClient {
public:
    virtual ~ValhallaMobileHttpClient() = default;
    
    /**
     * Makes a synchronous GET request to fetch tile data
     * @param url the URL to fetch
     * @param range_offset offset for range requests
     * @param range_size size for range requests, or 0 for the whole resource
     * @param accept_gzip whether a gzip body is acceptable (whole tiles with tile_url_gz on)
     * @return GET_response_t with the response data and status
     */
    virtual valhalla::baldr::tile_getter_t::GET_response_t
    get(const std::string& url, uint64_t range_offset, uint64_t range_size, bool accept_gzip) = 0;
    
    /**
     * Makes a synchronous HEAD request to fetch response headers
     * @param url the URL to query
     * @param header_mask mask for which headers to retrieve
     * @return HEAD_response_t with the response headers and status
     */
    virtual valhalla::baldr::tile_getter_t::HEAD_response_t 
    head(const std::string& url, valhalla::baldr::tile_getter_t::header_mask_t header_mask) = 0;
};

class RetryingGraphReader;

/**
 * Owns one Valhalla actor and runs actions against it.
 *
 * Every action runs on a dedicated thread with a 16 MB stack, joined before the call
 * returns: Valhalla's map matcher recurses once per matched edge, which overflows the
 * ~1 MB stack a mobile worker thread has on a long trace. The recursion is unbounded, so
 * this raises the ceiling rather than removing it. Actions stay synchronous, and anything
 * Valhalla calls back out of an action runs on that thread too — see valhalla_actor.cpp.
 *
 * The actor is not safe to use from several threads at once, and this class does not make
 * it so; the Kotlin and Obj-C wrappers serialize calls.
 */
class ValhallaActor {
private:
    /// Tile downloads that failed other than with a 404 or 410. Declared before the reader,
    /// whose tile getter counts into it.
    uint32_t fetch_failures = 0;
    /// How long the running action may spend downloading tiles, in steady_clock ticks (0 for
    /// no limit), and how long it has. The tile getter reads and adds to them.
    std::chrono::steady_clock::rep fetch_budget = 0;
    std::chrono::steady_clock::rep fetch_time = 0;
    /// Run by the tile getter before each request: stops the action once its budget is spent.
    std::function<void()> before_fetch;
    /// Passed to every action, which runs it during its search: stops it once cancelled.
    std::function<void()> interrupt;
    std::unique_ptr<valhalla::tyr::actor_t> actor;
    std::unique_ptr<RetryingGraphReader> graph_reader;

    /// `mjolnir.tile_url_timeout`, in seconds, or 0 for no limit.
    double tile_fetch_timeout_seconds = 0.0;
    /// Set when the running action ran out of time. Loki catches what a tile fetch throws
    /// during its search, so the action can't tell otherwise.
    bool timed_out = false;
    /// Set when a cancel stopped the running action, for the same reason.
    bool was_cancelled = false;
    /// The caller's cancel flag when given, else one that stays false.
    std::atomic<bool> owned_cancelled{false};
    std::atomic<bool>* cancelled = &owned_cancelled;

    /// Runs one action, after letting it retry tiles that failed to download in earlier ones.
    std::string run(const std::function<std::string()>& action);
public:
    /// @param cancel_flag  optional and not owned: set it to stop the running action before its
    ///                     next tile download or during its search. It must outlive the actor.
    ValhallaActor(const std::string& config_path,
                  ValhallaMobileHttpClient* http_client = nullptr,
                  std::atomic<bool>* cancel_flag = nullptr);
    ~ValhallaActor();

    /// The message of an action that failed after a tile download failed other than with a
    /// 404 or 410.
    static constexpr const char* kFetchFailedMessage = "valhalla-mobile: tile fetch failed";
    /// The message of an action that spent more than `mjolnir.tile_url_timeout` seconds
    /// downloading tiles.
    static constexpr const char* kTimedOutMessage = "valhalla-mobile: tile fetch deadline";
    /// The message of an action stopped by the cancel flag.
    static constexpr const char* kCancelledMessage = "valhalla-mobile: cancelled";

    /**
     * Compute a route between the given locations. This is Valhalla's `route`
     * action.
     *
     * @param request  a `route` request as JSON. See
     *                 https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/
     * @return         the serialized response, in whichever format the request asked for
     */
    std::string route(const std::string& request);

    /**
     * Map-match a GPS trace onto the road network and return a route along the
     * matched path. This is Valhalla's `trace_route` action.
     *
     * @param request  a `trace_route` request as JSON. See
     *                 https://valhalla.github.io/valhalla/api/map-matching/api-reference/
     * @return         the serialized response, in whichever format the request asked for
     */
    std::string trace_route(const std::string& request);

    /**
     * Map-match a GPS trace onto the road network and return the attributes of
     * every edge along the matched path. This is Valhalla's `trace_attributes`
     * action.
     *
     * Unlike `trace_route`, this action always answers with Valhalla's own JSON —
     * the `format` option does not apply to it.
     *
     * @param request  a `trace_attributes` request as JSON. See
     *                 https://valhalla.github.io/valhalla/api/map-matching/api-reference/
     * @return         the serialized JSON response
     */
    std::string trace_attributes(const std::string& request);

    /**
     * Sample terrain heights under a shape. This is Valhalla's `height` action.
     *
     * @param request  a `height` request as JSON. See
     *                 https://valhalla.github.io/valhalla/api/elevation/api-reference/
     * @return         the serialized JSON response
     */
    std::string height(const std::string& request);

    /**
     * Compute a matrix of costs and times between every source and every target. This is
     * Valhalla's `sources_to_targets` action.
     *
     * @param request  a `sources_to_targets` request as JSON. See
     *                 https://valhalla.github.io/valhalla/api/matrix/api-reference/
     * @return         the serialized response, in whichever format the request asked for
     */
    std::string matrix(const std::string& request);
};

#endif // VALHALLAACTOR_H
