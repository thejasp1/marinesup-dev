import SwiftUI

struct ContentView: View {
    @AppStorage("toonHost") private var savedHost = ""
    @State private var host = ""
    @State private var state: ConnectionState = .idle
    @State private var showSetup = false

    var body: some View {
        Group {
            if savedHost.isEmpty || showSetup {
                SetupView(host: $host, state: $state) { value in
                    await connect(host: value, save: true)
                }
            } else {
                DashboardView(host: savedHost, state: $state, onRefresh: { await connect(host: savedHost, save: false) }, onSetup: {
                    host = savedHost
                    showSetup = true
                })
            }
        }
        .task {
            if !savedHost.isEmpty {
                host = savedHost
                await connect(host: savedHost, save: false)
            }
        }
    }

    @MainActor private func connect(host: String, save: Bool) async {
        state = .testing
        do {
            let data = try await ToonAPI.shared.fetch(host: host)
            if save {
                savedHost = host
                showSetup = false
            }
            state = .connected(data)
        } catch {
            state = .failed("Geen geldige verbinding met Toon. Controleer wifi en IP-adres.")
        }
    }
}

private struct SetupView: View {
    @Binding var host: String
    @Binding var state: ConnectionState
    var connect: (String) async -> Void

    var body: some View {
        ZStack {
            LinearGradient(colors: [.black, Color(red: 0.03, green: 0.09, blue: 0.18), Color(red: 0.16, green: 0.04, blue: 0.20)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 22) {
                    Spacer(minLength: 30)
                    JSWLogo()
                    Text("TOON REMOTE").font(.system(size: 30, weight: .black, design: .rounded))
                    Text("ENTERPRISE FOR iPHONE").font(.caption.weight(.bold)).foregroundStyle(.cyan)
                    Text("Jasp Software").foregroundStyle(.secondary)

                    VStack(alignment: .leading, spacing: 16) {
                        Label("Koppel je Toon 2", systemImage: "link.circle.fill").font(.title2.bold())
                        Text("Vul het lokale IP-adres van je gerootte Toon in. Alleen een echte Toon-response geeft de status Verbonden.").foregroundStyle(.secondary)
                        TextField("Bijv. 192.168.1.100", text: $host)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.numbersAndPunctuation)
                            .padding()
                            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                        Button {
                            Task { await connect(host.trimmingCharacters(in: .whitespacesAndNewlines)) }
                        } label: {
                            HStack {
                                if case .testing = state { ProgressView().tint(.black) }
                                Image(systemName: "wifi")
                                Text("TEST & VERBIND").fontWeight(.black)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 16)
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.black)
                        .background(LinearGradient(colors: [.cyan, .purple, .orange], startPoint: .leading, endPoint: .trailing), in: RoundedRectangle(cornerRadius: 18))
                        .disabled(host.isEmpty)

                        if case .failed(let message) = state {
                            Label(message, systemImage: "exclamationmark.triangle.fill")
                                .font(.footnote).foregroundStyle(.red)
                        }
                    }
                    .padding(22)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28))
                    .padding(.horizontal)

                    Text("Lokaal gebruikt de app poort 80. Voor externe bediening wordt later alleen beveiligde HTTPS/443 gebruikt.")
                        .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal, 30)
                }
            }
        }
    }
}

private struct DashboardView: View {
    let host: String
    @Binding var state: ConnectionState
    var onRefresh: () async -> Void
    var onSetup: () -> Void
    @State private var tab = 0

    private var data: ToonData? {
        if case .connected(let d) = state { return d }
        return nil
    }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { home }.tabItem { Label("Dashboard", systemImage: "house.fill") }.tag(0)
            NavigationStack { placeholder("Programma", "calendar.badge.clock", .purple) }.tabItem { Label("Programma", systemImage: "calendar") }.tag(1)
            NavigationStack { placeholder("Energie", "bolt.fill", .green) }.tabItem { Label("Energie", systemImage: "bolt.fill") }.tag(2)
            NavigationStack { settings }.tabItem { Label("Instellingen", systemImage: "gearshape.fill") }.tag(3)
        }
        .tint(.cyan)
    }

    private var home: some View {
        ZStack {
            LinearGradient(colors: [.black, Color(red: 0.03, green: 0.10, blue: 0.20)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 18) {
                    HStack {
                        JSWLogo(compact: true)
                        Spacer()
                        statusPill
                    }
                    .padding(.horizontal)

                    if let data {
                        Text("KLIMAATCENTRUM").font(.caption.weight(.bold)).tracking(2).foregroundStyle(.secondary)
                        ThermostatCard(host: host, data: data) { target in
                            Task {
                                try? await ToonAPI.shared.setTemperature(host: host, celsius: target)
                                await onRefresh()
                            }
                        }
                        HStack(spacing: 12) {
                            metric("BINNEN", String(format: "%.1f°", data.roomTemperature), "thermometer.medium", .cyan)
                            metric("DOEL", String(format: "%.1f°", data.setpoint), "scope", .purple)
                        }
                        HStack(spacing: 12) {
                            metric("KETEL", data.heating ? "AAN" : "UIT", "flame.fill", .orange)
                            metric("TOON", "ONLINE", "checkmark.shield.fill", .green)
                        }
                    } else {
                        VStack(spacing: 14) {
                            Image(systemName: "wifi.exclamationmark").font(.system(size: 44)).foregroundStyle(.red)
                            Text("Geen Toon verbonden").font(.title2.bold())
                            Text("De app toont geen demodata. Controleer de verbinding of pas het IP-adres aan.").foregroundStyle(.secondary).multilineTextAlignment(.center)
                            Button("Verbinding opnieuw testen") { Task { await onRefresh() } }
                            Button("Toon opnieuw instellen", action: onSetup)
                        }.padding(24).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 28)).padding()
                    }
                }.padding(.vertical)
            }
        }
        .navigationBarHidden(true)
    }

    private var statusPill: some View {
        HStack(spacing: 7) {
            Circle().fill(data == nil ? Color.red : Color.green).frame(width: 8, height: 8)
            Text(data == nil ? "OFFLINE" : "VERBONDEN").font(.caption2.bold())
        }.padding(.horizontal, 12).padding(.vertical, 8).background(.thinMaterial, in: Capsule())
    }

    private func metric(_ title: String, _ value: String, _ icon: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon).font(.caption.bold()).foregroundStyle(color)
            Text(value).font(.title2.bold())
        }.frame(maxWidth: .infinity, alignment: .leading).padding().background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 20))
    }

    private func placeholder(_ title: String, _ icon: String, _ color: Color) -> some View {
        ZStack {
            LinearGradient(colors: [.black, color.opacity(0.18)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: icon).font(.system(size: 52)).foregroundStyle(color)
                Text(title).font(.largeTitle.bold())
                Text("Dit scherm wordt alleen gevuld met gegevens die werkelijk door jouw Toon beschikbaar worden gesteld.").foregroundStyle(.secondary).multilineTextAlignment(.center).padding()
            }
        }
    }

    private var settings: some View {
        Form {
            Section("Toon") {
                LabeledContent("IP-adres", value: host)
                LabeledContent("Lokale poort", value: "80")
                Button("Verbinding testen") { Task { await onRefresh() } }
                Button("Toon opnieuw instellen", action: onSetup)
            }
            Section("Remote") {
                LabeledContent("Externe toegang", value: "HTTPS / 443")
                Text("Remote gateway wordt als volgende koppellaag toegevoegd. Poort 80 wordt niet rechtstreeks aan internet gepubliceerd.")
            }
            Section("App") {
                LabeledContent("Product", value: "Toon Remote Enterprise")
                LabeledContent("Ontwikkelaar", value: "Jasp Software")
                LabeledContent("Versie", value: "1.0")
            }
        }.navigationTitle("Instellingen")
    }
}

private struct ThermostatCard: View {
    let host: String
    let data: ToonData
    var setTarget: (Double) -> Void

    var body: some View {
        VStack(spacing: 18) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Woonkamer").font(.title2.bold())
                    Text("Live van Toon · \(host)").font(.caption).foregroundStyle(.green)
                }
                Spacer()
                Label(data.heating ? "VERWARMEN" : "STAND-BY", systemImage: "flame.fill")
                    .font(.caption2.bold()).foregroundStyle(data.heating ? .orange : .green)
            }
            ZStack {
                Circle().stroke(Color.white.opacity(0.08), lineWidth: 18)
                Circle().trim(from: 0.08, to: 0.92).stroke(AngularGradient(colors: [.cyan, .blue, .purple, .orange, .pink], center: .center), style: StrokeStyle(lineWidth: 18, lineCap: .round)).rotationEffect(.degrees(90))
                VStack(spacing: 4) {
                    Text(String(format: "%.1f°", data.roomTemperature)).font(.system(size: 54, weight: .light, design: .rounded))
                    Text("BINNEN").font(.caption2.bold()).foregroundStyle(.secondary)
                }
            }.frame(width: 230, height: 230)
            HStack(spacing: 28) {
                Button { setTarget(max(5, data.setpoint - 0.5)) } label: { Image(systemName: "minus").frame(width: 52, height: 52).background(.blue.opacity(0.22), in: Circle()) }
                VStack { Text(String(format: "%.1f°", data.setpoint)).font(.title.bold()); Text("INGESTELD").font(.caption2).foregroundStyle(.secondary) }
                Button { setTarget(min(30, data.setpoint + 0.5)) } label: { Image(systemName: "plus").frame(width: 52, height: 52).background(.purple.opacity(0.22), in: Circle()) }
            }.buttonStyle(.plain)
        }
        .padding(22)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 30))
        .padding(.horizontal)
    }
}

private struct JSWLogo: View {
    var compact = false
    var body: some View {
        VStack(spacing: compact ? 2 : 6) {
            Text("JSW")
                .font(.system(size: compact ? 25 : 52, weight: .black, design: .rounded))
                .foregroundStyle(LinearGradient(colors: [.cyan, .blue, .purple, .pink, .orange], startPoint: .leading, endPoint: .trailing))
            if !compact { Text("JASP SOFTWARE").font(.caption.bold()).tracking(3).foregroundStyle(.secondary) }
        }
    }
}
