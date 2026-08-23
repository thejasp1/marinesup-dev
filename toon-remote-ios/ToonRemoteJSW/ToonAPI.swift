import Foundation

actor ToonAPI {
    static let shared = ToonAPI()

    func fetch(host: String) async throws -> ToonData {
        guard let url = URL(string: "http://\(host)/happ_thermstat?action=getThermostatInfo") else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 6
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        func temp(_ key: String) -> Double {
            if let n = json[key] as? NSNumber { let v = n.doubleValue; return v > 100 ? v / 100.0 : v }
            if let s = json[key] as? String, let v = Double(s) { return v > 100 ? v / 100.0 : v }
            return 0
        }
        let room = temp("currentTemp")
        let set = temp("currentSetpoint")
        guard room > 0, set > 0 else { throw URLError(.cannotParseResponse) }
        let burner = (json["burnerInfo"] as? NSNumber)?.intValue ?? Int(json["burnerInfo"] as? String ?? "0") ?? 0
        let state = (json["activeState"] as? NSNumber)?.intValue ?? 0
        return ToonData(roomTemperature: room, setpoint: set, heating: burner > 0, activeState: state)
    }

    func setTemperature(host: String, celsius: Double) async throws {
        let value = Int((celsius * 100).rounded())
        guard let url = URL(string: "http://\(host)/happ_thermstat?action=changeTemperature&Setpoint=\(value)") else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.timeoutInterval = 6
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { throw URLError(.badServerResponse) }
    }
}
