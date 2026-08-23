import Foundation

struct ToonData: Equatable {
    var roomTemperature: Double
    var setpoint: Double
    var heating: Bool
    var activeState: Int
}

enum ConnectionState: Equatable {
    case idle
    case testing
    case connected(ToonData)
    case failed(String)
}
