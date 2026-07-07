package io.github.dongyuzhao.mathrenderer.sample

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.dongyuzhao.mathrenderer.FormulaRenderOptions
import io.github.dongyuzhao.mathrenderer.MathRenderer
import io.github.dongyuzhao.mathrenderer.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Sample Activity demonstrating the MathRenderer Android library.
 *
 * Enter any LaTeX formula (without surrounding `$` delimiters) and tap
 * **Render** to rasterise it with MathJax and display the result.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.renderButton.setOnClickListener { renderFormula() }

        binding.latexInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                renderFormula()
                true
            } else {
                false
            }
        }

        // Render the default formula on launch so the screen is not empty.
        renderFormula()
    }

    private fun renderFormula() {
        val latex = binding.latexInput.text?.toString()?.trim() ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.formulaImage.visibility = View.INVISIBLE
        binding.statusText.text = getString(R.string.status_rendering)
        binding.renderButton.isEnabled = false

        lifecycleScope.launch {
            val opts = FormulaRenderOptions(
                standalone = false,
                fontSize = 24.0,
                scale = resources.displayMetrics.density.toDouble(),
            )
            val image = MathRenderer.render(
                context = this@MainActivity,
                latex = latex,
                options = opts,
                color = Color.BLACK,
                density = resources.displayMetrics.density,
            )

            binding.progressBar.visibility = View.GONE
            binding.renderButton.isEnabled = true

            if (image != null) {
                binding.formulaImage.setImageBitmap(image.bitmap)
                binding.formulaImage.visibility = View.VISIBLE
                binding.statusText.text = getString(R.string.status_success)
            } else {
                binding.formulaImage.setImageBitmap(null)
                binding.formulaImage.visibility = View.INVISIBLE
                binding.statusText.text = getString(R.string.status_error)
            }
        }
    }
}
