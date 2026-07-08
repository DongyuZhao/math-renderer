import { Math } from "react-math";
import "./styles.css";

const BODY_FONT = 18;

export function App() {
    return (
        <main className="app-shell">
            <section className="intro">
                <p className="eyebrow">react-math</p>
                <h1>LaTeX to native SVG</h1>
                <p>
                    The same MathJax core used by Swift and Android. Formulas appear in the three
                    modes we name across the stack — <strong>embedded</strong>,{" "}
                    <strong>standalone</strong>, and <strong>block</strong>. In this API they map to{" "}
                    <code>displayMode</code>: embedded → <code>"inline"</code> (
                    <code>standalone: false</code>); standalone and block → <code>"block"</code> (
                    <code>standalone: true</code>, display style).
                </p>
            </section>

            <article className="article">
                <h2>1 · Embedded</h2>
                <p>
                    Embedded math (inline <code>$…$</code>) sits on the text baseline and follows
                    the surrounding font size, so a result like{" "}
                    <Math tex="a^2 + b^2 = c^2" displayMode="inline" fontSize={BODY_FONT} /> reads
                    as part of the sentence. A quadratic{" "}
                    <Math tex="ax^2 + bx + c = 0" displayMode="inline" fontSize={BODY_FONT} /> has
                    real roots whenever its discriminant{" "}
                    <Math tex="b^2 - 4ac" displayMode="inline" fontSize={BODY_FONT} /> is
                    non-negative. Science works mid-sentence too: Einstein's{" "}
                    <Math tex="E = mc^2" displayMode="inline" fontSize={BODY_FONT} />, a Gaussian
                    integral{" "}
                    <Math
                        tex="\int_0^\infty e^{-x^2}\,dx"
                        displayMode="inline"
                        fontSize={BODY_FONT}
                    />
                    , and even chemistry such as{" "}
                    <Math tex="\ce{2H2 + O2 -> 2H2O}" displayMode="inline" fontSize={BODY_FONT} />.
                </p>

                <h2>2 · Standalone</h2>
                <p>
                    A standalone formula (<code>$$…$$</code>) is rendered block-style — larger
                    operators, full-height fractions and radicals — and isolated on its own centered
                    line, in the flow of the surrounding text:
                </p>
                <div className="equation">
                    <Math
                        tex="x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}"
                        displayMode="block"
                        fontSize={26}
                        className="equation-math"
                    />
                </div>
                <p>
                    Sums and limits keep their bounds above and below the operator, which embedded
                    mode would otherwise shrink:
                </p>
                <div className="equation">
                    <Math
                        tex="\sum_{n=1}^{\infty} \frac{1}{n^2} = \frac{\pi^2}{6}"
                        displayMode="block"
                        fontSize={26}
                        className="equation-math"
                    />
                </div>

                <h2>3 · Block</h2>
                <p>
                    When a formula is the sole content of its block, it becomes a top-level block
                    element — the same display render, presented on its own as a figure:
                </p>
                <figure className="standalone">
                    <Math
                        tex="e^{i\pi} + 1 = 0"
                        displayMode="block"
                        fontSize={40}
                        className="equation-math"
                    />
                    <figcaption>Euler's identity</figcaption>
                </figure>
            </article>
        </main>
    );
}
