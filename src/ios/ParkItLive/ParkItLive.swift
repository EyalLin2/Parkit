import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct ParkItLiveBundle: WidgetBundle {
    var body: some Widget {
        ParkedLiveActivity()
    }
}

/// Never runs: the system performs LeaveSpotIntent in the app's process.
enum ParkedHandOff {
    static func run() async {}
}

private let brand = Color(red: 0x1B / 255, green: 0x4F / 255, blue: 0x91 / 255)
private let green = Color(red: 0x18 / 255, green: 0x80 / 255, blue: 0x38 / 255)

private struct LeaveButton: View {
    let label: String
    var body: some View {
        Button(intent: LeaveSpotIntent()) {
            Text(label).font(.system(size: 14, weight: .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.8)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(green, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

private struct ParkedCard: View {
    let attributes: ParkedAttributes
    let state: ParkedAttributes.ContentState

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "car.fill").font(.system(size: 18)).foregroundStyle(.white)
                .frame(width: 40, height: 40).background(brand, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(state.title).font(.system(size: 15, weight: .bold)).lineLimit(2)
                if let message = state.message {
                    Text(message).font(.system(size: 13)).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if state.canLeave { LeaveButton(label: attributes.leaveLabel) }
        }
        .padding(14)
        .environment(\.layoutDirection, attributes.rightToLeft ? .rightToLeft : .leftToRight)
    }
}

struct ParkedLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ParkedAttributes.self) { context in
            ParkedCard(attributes: context.attributes, state: context.state)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.bottom) {
                    ParkedCard(attributes: context.attributes, state: context.state)
                }
            } compactLeading: {
                Image(systemName: "car.fill").foregroundStyle(.white)
            } compactTrailing: {
                Text(verbatim: "P").font(.system(size: 14, weight: .black)).foregroundStyle(.white)
            } minimal: {
                Image(systemName: "car.fill").foregroundStyle(.white)
            }
        }
    }
}
