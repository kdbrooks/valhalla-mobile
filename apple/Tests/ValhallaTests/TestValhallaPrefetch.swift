import Foundation
import XCTest
import ValhallaConfigModels
@testable import Valhalla

/// `tilesCovering`, and `ensureTileCached` against the fixtures served by `FixtureTileProtocol`.
final class TestValhallaPrefetch: XCTestCase {

    private static let tile: UInt32 = 762_485
    private static let missingTile: UInt32 = 762_484

    private var root: URL!

    private var tilesDir: URL { root.appendingPathComponent("tiles", isDirectory: true) }

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("prefetch-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tilesDir, withIntermediateDirectories: true)
        URLProtocol.registerClass(FixtureTileProtocol.self)
    }

    override func tearDownWithError() throws {
        URLProtocol.unregisterClass(FixtureTileProtocol.self)
        if let root {
            try? FileManager.default.removeItem(at: root)
        }
    }

    /// A new engine on the fixture server.
    private func valhalla() throws -> Valhalla {
        let config = try ValhallaConfig(
            tilesUrl: "http://tiles.invalid/{tilePath}",
            tilesDir: tilesDir,
            tilesAreGzFiles: false)
        return try Valhalla(config, configName: "prefetch.json")
    }

    func testTilesCoveringACoordinate() throws {
        XCTAssertEqual(try valhalla().tilesCovering(latitude: 42.5063, longitude: 1.5218), [
            TileRef(level: 0, id: 3015, path: "0/003/015.gph"),
            TileRef(level: 1, id: 47701, path: "1/047/701.gph"),
            TileRef(level: 2, id: 763_926, path: "2/000/763/926.gph")
        ])
    }

    func testFetchesATileTheOriginHas() throws {
        XCTAssertTrue(try valhalla().ensureTileCached(level: 2, id: Self.tile))

        XCTAssertTrue(FileManager.default.fileExists(
            atPath: tilesDir.appendingPathComponent("2/000/762/485.gph").path))
    }

    func testReturnsFalseForATileTheOriginDoesNotHave() throws {
        XCTAssertFalse(try valhalla().ensureTileCached(level: 2, id: Self.missingTile))
    }

    /// The error a prefetch throws reaches Swift through its own bridge, here a cancel's.
    func testThrowsWhenCancelled() throws {
        let valhalla = try valhalla()
        valhalla.cancel()

        XCTAssertThrowsError(try valhalla.ensureTileCached(level: 2, id: Self.tile)) { error in
            XCTAssertEqual(error as? ValhallaError, .valhallaError(-1, "valhalla-mobile: cancelled"))
        }

        valhalla.resume()
        XCTAssertTrue(try valhalla.ensureTileCached(level: 2, id: Self.tile))
    }
}
