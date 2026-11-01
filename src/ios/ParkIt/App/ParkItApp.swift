import SwiftUI

@main
struct ParkItApp: App {
    @UIApplicationDelegateAdaptor(ParkItAppDelegate.self) private var appDelegate
    @StateObject private var session = Session.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .id(session.language) // a new language rebuilds every screen, like Android's recreate()
                .environmentObject(session)
                .parkItEnvironment(session)
                .onChange(of: phase) { _, new in onPhase(new) }
                .task {
                    // While the app is open the session is renewed every few minutes.
                    while !Task.isCancelled {
                        try? await Task.sleep(for: .seconds(600))
                        await renew()
                    }
                }
        }
    }

    private func onPhase(_ new: ScenePhase) {
        switch new {
        case .background:
            session.leftAppAt = Date()
        case .active:
            // Sessions end after two weeks away (the server enforces the same window).
            if session.token != nil, let left = session.leftAppAt, Date().timeIntervalSince(left) > 14 * 86400 {
                session.signOut()
            } else {
                Task { await renew() }
            }
        default:
            break
        }
    }

    private func renew() async {
        guard session.token != nil, let fresh = try? await API.shared.refreshToken(), session.token != nil else { return }
        session.save(token: fresh.accessToken, userId: fresh.userId, displayName: session.displayName ?? "")
    }
}

enum Route: Hashable {
    case profile, rewards(tab: Int), driver, settings, detailsEdit, tips
}

struct RootView: View {
    @EnvironmentObject private var session: Session

    var body: some View {
        Group {
            switch session.root {
            case .intro:
                IntroScreen {
                    session.introSeen = true
                    session.root = .login
                }
            case .login:
                LoginScreen()
            case .details:
                DetailsScreen(firstTime: true) {
                    session.needsProfile = false
                    session.root = session.afterDetails
                }
            case .onboarding:
                DriverProfileScreen(onboarding: true) {
                    session.root = session.afterCarStep
                }
            case .tips:
                TipsScreen {
                    session.tipsSeen = true
                    session.root = .main
                }
            case .main:
                MainStack()
            }
        }
        .animation(.easeInOut(duration: 0.28), value: session.root)
    }
}

/// The map and everything behind it. Screens draw their own top bars, as on Android.
struct MainStack: View {
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            MapScreen(onOpenProfile: { path.append(.profile) })
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { route in
                    Group {
                        switch route {
                        case .profile:
                            ProfileScreen(onBack: { path.removeLast() }, open: { path.append($0) })
                        case .rewards(let tab):
                            RewardsScreen(initialTab: tab, onBack: { path.removeLast() })
                        case .driver:
                            DriverProfileScreen(onboarding: false) { path.removeLast() }
                        case .settings:
                            SettingsScreen(onBack: { path.removeLast() }, open: { path.append($0) })
                        case .detailsEdit:
                            DetailsScreen(firstTime: false) { path.removeLast() }
                        case .tips:
                            TipsScreen {
                                Session.shared.tipsSeen = true
                                path.removeLast()
                            }
                        }
                    }
                    .toolbar(.hidden, for: .navigationBar)
                }
        }
    }
}
