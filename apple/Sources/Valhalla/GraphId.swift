import Foundation

/// Valhalla's `baldr::GraphId`: a hierarchy level, a tile within it, and an id within the tile.
public struct GraphId: Codable, Hashable {
    public let level: UInt32
    public let tileId: UInt32
    public let id: UInt32

    public init(level: UInt32, tileId: UInt32, id: UInt32 = 0) {
        self.level = level
        self.tileId = tileId
        self.id = id
    }

    enum CodingKeys: String, CodingKey {
        case level
        case tileId = "tile_id"
        case id
    }
}

/// The tileset identity fields of Valhalla's `baldr::GraphTileHeader`.
public struct GraphTileHeader: Decodable, Hashable {
    public let graphId: GraphId
    public let version: String
    public let datasetId: UInt64
    public let dateCreated: UInt32

    enum CodingKeys: String, CodingKey {
        case graphId = "graphid"
        case version
        case datasetId = "dataset_id"
        case dateCreated = "date_created"
    }
}

struct TileIdsRequest: Encodable {
    let minx: Double
    let miny: Double
    let maxx: Double
    let maxy: Double
    let levels: [UInt32]
}

struct TileIdsResponse: Decodable {
    let tileIds: [GraphId]

    enum CodingKeys: String, CodingKey {
        case tileIds = "tile_ids"
    }
}

struct GraphTileHeaderResponse: Decodable {
    let header: GraphTileHeader?
}
