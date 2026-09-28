// Mythos – dünne native Hülle um die Website. Chat und Code-Fernsteuerung sind normale Webseiten;
// ändert sich die Website, muss diese App nicht neu gebaut werden.
import SwiftUI

@main
struct MythosHandyApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

private let siteBase = URL(string: "__SITE__")!

struct RootView: View {
    var body: some View {
        TabView {
            WebTab(url: siteBase.appendingPathComponent("app"))
                .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right.fill") }
            WebTab(url: siteBase.appendingPathComponent("code"))
                .tabItem { Label("Code", systemImage: "terminal.fill") }
        }
        .preferredColorScheme(.dark)
    }
}
