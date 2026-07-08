import SwiftMath
import SwiftUI

@main
struct SwiftMathSampleApp: App {
    var body: some Scene {
        WindowGroup {
            SampleContentView()
        }
    }
}

private let bodyFont: CGFloat = 18

private struct SampleContentView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("SwiftMath")
                            .font(.largeTitle.weight(.semibold))
                        Text(
                            "SwiftUI rendering backed by the shared MathJax core, in the three modes "
                                + "we name across the stack: embedded, standalone, and block."
                        )
                        .foregroundStyle(.secondary)
                    }

                    embeddedSection
                    standaloneSection
                    blockSection
                }
                .padding(20)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Math Sample")
        }
    }

    // Embedded: inline `$…$`, standalone = false — sized to the surrounding text
    // and sitting on its baseline. (A full document renderer flows these inside
    // wrapping paragraphs via TextKit NSTextAttachment; here each fragment ends
    // at its formula, since SwiftUI's HStack cannot reflow a view mid-sentence.)
    private var embeddedSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader("1 · Embedded")
            Text("Embedded math is sized to the surrounding text and sits on its baseline:")
                .foregroundStyle(.secondary)
            embeddedRow("For a right triangle,", "a^2 + b^2 = c^2")
            embeddedRow("Einstein showed", "E = mc^2")
            embeddedRow("Water forms via", #"\ce{2H2 + O2 -> 2H2O}"#)
        }
    }

    private func embeddedRow(_ lead: String, _ tex: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(lead)
            MathText(tex, displayMode: .inline, fontSize: bodyFont)
        }
        .font(.system(size: bodyFont))
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // Standalone: `$$…$$`, standalone = true — display style, isolated on its own line in-flow.
    private var standaloneSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader("2 · Standalone")
            Text("A standalone formula is rendered block-style on its own centered line:")
                .foregroundStyle(.secondary)
            MathText(#"x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}"#, displayMode: .block, fontSize: 24)
                .frame(maxWidth: .infinity, alignment: .center)
            MathText(
                #"\sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}"#,
                displayMode: .block,
                fontSize: 24
            )
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    // Block: a standalone formula that is the sole content of its block, promoted to a block element.
    private var blockSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader("3 · Block")
            Text("The same display render, presented on its own as a block-level figure:")
                .foregroundStyle(.secondary)
            VStack(spacing: 10) {
                MathText(#"e^{i\pi} + 1 = 0"#, displayMode: .block, fontSize: 34)
                Text("Euler's identity")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(.background)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(.quaternary))
        }
    }
}

private struct SectionHeader: View {
    let title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title.uppercased())
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Color.teal)
    }
}
