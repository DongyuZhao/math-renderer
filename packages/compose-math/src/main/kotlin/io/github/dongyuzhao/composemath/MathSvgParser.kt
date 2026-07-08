package io.github.dongyuzhao.composemath

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Parses MathJax SVG markup into a [MathSvgDocument] using a hardened SAX pass.
 *
 * This is a port of the iOS `MathSVGParser`. It intentionally accepts only the small subset of
 * SVG that MathJax emits (`svg`, `defs`, `g`, `path`, `use`, `rect`, `text`, plus metadata) and
 * rejects anything else — DOCTYPE/entity declarations, scriptable attributes, external
 * references, oversized documents — so untrusted markup cannot escalate into script or external
 * resource loading.
 */
object MathSvgParser {
    private const val MAX_SVG_BYTE_COUNT = 256_000

    fun parse(markup: String): MathSvgDocument? {
        val bytes = markup.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_SVG_BYTE_COUNT || containsForbiddenMarkupDeclaration(markup)) {
            return null
        }

        val handler = Handler()
        return try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            factory.setSecureFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setSecureFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setSecureFeature("http://xml.org/sax/features/external-parameter-entities", false)

            val parser = factory.newSAXParser()
            parser.setLexicalHandlerSafely(handler)

            val source = InputSource(ByteArrayInputStream(bytes))
            parser.parse(source, handler)

            if (handler.rejected) null else handler.document
        } catch (_: Exception) {
            null
        }
    }

    private fun SAXParserFactory.setSecureFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun javax.xml.parsers.SAXParser.setLexicalHandlerSafely(handler: DefaultHandler2) {
        runCatching {
            xmlReader.setProperty(
                "http://xml.org/sax/properties/lexical-handler",
                handler,
            )
        }
    }

    private fun containsForbiddenMarkupDeclaration(markup: String): Boolean = Regex(
        "<!\\s*(doctype|entity|element|attlist|notation)\\b",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(markup)

    private class Handler : DefaultHandler2() {
        private val paths = HashMap<String, MathSvgPath>()
        private val drawCommands = ArrayList<MathSvgDrawCommand>()
        private val textCommands = ArrayList<MathSvgTextCommand>()
        var rejected = false
            private set

        private var viewBox: MathJaxViewBox? = null
        private var sawRootSvg = false
        private var elementCount = 0
        private val elementStack = ArrayList<String>()
        private var defsDepth = 0
        private val transformStack = ArrayList<MathSvgMatrix>().apply { add(MathSvgMatrix.Identity) }
        private var pendingText: PendingText? = null

        val document: MathSvgDocument?
            get() {
                val box = viewBox
                if (!sawRootSvg || box == null) {
                    return null
                }
                return MathSvgDocument(
                    viewBox = box,
                    drawCommands = drawCommands,
                    textCommands = textCommands,
                )
            }

        private val currentTransform: MathSvgMatrix
            get() = transformStack.lastOrNull() ?: MathSvgMatrix.Identity

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            if (rejected) return

            elementCount += 1
            if (elementCount > MAX_ELEMENT_COUNT) {
                reject()
                return
            }

            val element = localName(qName ?: localName ?: "")
            val attributeMap = attributeMap(attributes)
            if (!validateElementPosition(element) || !validateAttributes(attributeMap)) {
                return
            }

            when (element) {
                "svg" -> {
                    val parsedViewBox = parseViewBox(attribute("viewBox", attributeMap))
                    if (parsedViewBox == null) {
                        reject()
                        return
                    }
                    viewBox = parsedViewBox
                    sawRootSvg = true
                }

                "defs" -> defsDepth += 1

                "g" -> {
                    val localTransform = parseElementTransform(attributeMap)
                    if (localTransform == null) {
                        reject()
                        return
                    }
                    transformStack.add(localTransform.concatenating(currentTransform))
                }

                "path" -> parsePath(attributeMap)
                "use" -> parseUse(attributeMap)
                "rect" -> parseRect(attributeMap)
                "text" -> beginText(attributeMap)
                "title", "desc", "metadata" -> Unit

                else -> {
                    reject()
                    return
                }
            }

            elementStack.add(element)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (rejected) return

            val element = localName(qName ?: localName ?: "")
            when (element) {
                "defs" -> defsDepth = maxOf(0, defsDepth - 1)
                "g" -> {
                    if (transformStack.size <= 1) {
                        reject()
                        return
                    }
                    transformStack.removeAt(transformStack.size - 1)
                }
                "text" -> finishText()
            }

            if (elementStack.lastOrNull() != element) {
                reject()
                return
            }
            elementStack.removeAt(elementStack.size - 1)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (rejected) return

            val string = String(ch, start, length)
            val text = pendingText
            if (text != null) {
                val nextValue = text.value + string
                if (nextValue.toByteArray(Charsets.UTF_8).size > MAX_TEXT_BYTE_COUNT) {
                    reject()
                    return
                }
                text.value = nextValue
                return
            }

            if (string.any { !it.isWhitespace() }) {
                reject()
            }
        }

        override fun startCDATA() {
            if (rejected) return
            reject()
        }

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            reject()
        }

        override fun startEntity(name: String?) {
            reject()
        }

        private fun beginText(attributes: Map<String, String>) {
            val x = numberAttribute("x", attributes, 0f)
            val y = numberAttribute("y", attributes, 0f)
            val localTransform = parseElementTransform(attributes)
            if (defsDepth != 0 || pendingText != null || x == null || y == null || localTransform == null) {
                reject()
                return
            }

            pendingText = PendingText(
                value = "",
                x = x,
                y = y,
                transform = localTransform.concatenating(currentTransform),
            )
        }

        private fun finishText() {
            val text = pendingText
            if (text == null) {
                reject()
                return
            }
            pendingText = null

            if (textCommands.size >= MAX_TEXT_COMMAND_COUNT) {
                reject()
                return
            }

            if (text.value.none { !it.isWhitespace() }) {
                return
            }

            textCommands.add(
                MathSvgTextCommand(
                    text = text.value,
                    x = text.x,
                    y = text.y,
                    transform = text.transform,
                ),
            )
        }

        private fun parsePath(attributes: Map<String, String>) {
            val pathData = attribute("d", attributes)
            if (pathData == null ||
                pathData.toByteArray(Charsets.UTF_8).size > MAX_ATTRIBUTE_BYTE_COUNT
            ) {
                reject()
                return
            }
            val path = SvgPathParser.parse(pathData)
            if (path == null) {
                reject()
                return
            }

            if (defsDepth > 0) {
                val id = attribute("id", attributes)
                if (attribute("transform", attributes) != null ||
                    id.isNullOrEmpty() ||
                    paths.size >= MAX_PATH_COUNT ||
                    paths.containsKey(id)
                ) {
                    reject()
                    return
                }
                paths[id] = path
            } else {
                val localTransform = parseElementTransform(attributes)
                if (localTransform == null) {
                    reject()
                    return
                }
                appendDrawCommand(
                    MathSvgDrawCommand(path, localTransform.concatenating(currentTransform)),
                )
            }
        }

        private fun parseUse(attributes: Map<String, String>) {
            val reference = attribute("href", attributes)
            if (defsDepth != 0 ||
                attribute("transform", attributes) != null ||
                reference == null ||
                !reference.startsWith("#") ||
                reference.length <= 1 ||
                reference.contains("://")
            ) {
                reject()
                return
            }

            val id = reference.substring(1)
            val path = paths[id]
            val x = numberAttribute("x", attributes, 0f)
            val y = numberAttribute("y", attributes, 0f)
            if (path == null || x == null || y == null) {
                reject()
                return
            }

            val localTransform = MathSvgMatrix.translation(x, y)
            appendDrawCommand(
                MathSvgDrawCommand(path, localTransform.concatenating(currentTransform)),
            )
        }

        private fun parseRect(attributes: Map<String, String>) {
            val x = numberAttribute("x", attributes, 0f)
            val y = numberAttribute("y", attributes, 0f)
            val width = requiredNumberAttribute("width", attributes)
            val height = requiredNumberAttribute("height", attributes)
            val localTransform = parseElementTransform(attributes)
            if (defsDepth != 0 ||
                x == null ||
                y == null ||
                width == null ||
                height == null ||
                width < 0f ||
                height < 0f ||
                localTransform == null
            ) {
                reject()
                return
            }

            val path = MathSvgPath(
                listOf(
                    MathSvgPathSegment.MoveTo(x, y),
                    MathSvgPathSegment.LineTo(x + width, y),
                    MathSvgPathSegment.LineTo(x + width, y + height),
                    MathSvgPathSegment.LineTo(x, y + height),
                    MathSvgPathSegment.Close,
                ),
            )
            appendDrawCommand(
                MathSvgDrawCommand(path, localTransform.concatenating(currentTransform)),
            )
        }

        private fun appendDrawCommand(command: MathSvgDrawCommand) {
            if (drawCommands.size >= MAX_DRAW_COMMAND_COUNT) {
                reject()
                return
            }
            drawCommands.add(command)
        }

        private fun validateElementPosition(element: String): Boolean {
            if (elementStack.isEmpty()) {
                if (element != "svg" || sawRootSvg) {
                    reject()
                    return false
                }
            } else {
                if (!sawRootSvg || element == "svg") {
                    reject()
                    return false
                }
            }
            return true
        }

        private fun validateAttributes(attributes: Map<String, String>): Boolean {
            for ((name, value) in attributes) {
                val localName = localName(name).lowercase()
                if (localName.startsWith("on") ||
                    value.contains("javascript:", ignoreCase = true)
                ) {
                    reject()
                    return false
                }
                if (localName == "href" && (!value.startsWith("#") || value.contains("://"))) {
                    reject()
                    return false
                }
            }
            return true
        }

        private fun reject() {
            rejected = true
            throw StopParsingException
        }

        private fun attributeMap(attributes: Attributes): Map<String, String> {
            val map = LinkedHashMap<String, String>(attributes.length)
            for (i in 0 until attributes.length) {
                val name = attributes.getQName(i).ifEmpty { attributes.getLocalName(i) }
                if (name.isNotEmpty()) {
                    map[name] = attributes.getValue(i)
                }
            }
            return map
        }

        private fun attribute(name: String, attributes: Map<String, String>): String? {
            attributes[name]?.let { return it }
            return attributes.entries.firstOrNull { localName(it.key) == name }?.value
        }

        private fun numberAttribute(name: String, attributes: Map<String, String>, defaultValue: Float): Float? {
            val value = attribute(name, attributes) ?: return defaultValue
            return parseNumber(value)
        }

        private fun requiredNumberAttribute(name: String, attributes: Map<String, String>): Float? {
            val value = attribute(name, attributes) ?: return null
            return parseNumber(value)
        }

        private fun parseNumber(value: String): Float? {
            val numbers = NumberListParser.parse(value, 1)
            if (numbers == null || numbers.size != 1) {
                return null
            }
            return numbers[0]
        }

        private fun parseViewBox(value: String?): MathJaxViewBox? {
            if (value == null || value.toByteArray(Charsets.UTF_8).size > MAX_VIEWBOX_BYTE_COUNT) {
                return null
            }
            val numbers = NumberListParser.parse(value, 4)
            if (numbers == null || numbers.size != 4 || numbers[2] <= 0f || numbers[3] <= 0f) {
                return null
            }
            return MathJaxViewBox(numbers[0], numbers[1], numbers[2], numbers[3])
        }

        private fun parseElementTransform(attributes: Map<String, String>): MathSvgMatrix? {
            val transform = attribute("transform", attributes) ?: return MathSvgMatrix.Identity
            if (transform.toByteArray(Charsets.UTF_8).size > MAX_TRANSFORM_BYTE_COUNT) {
                return null
            }
            return parseTransform(transform)
        }

        private fun parseTransform(value: String): MathSvgMatrix? {
            val expression = Regex("([A-Za-z]+)\\s*\\(([^)]*)\\)")
            var composed = MathSvgMatrix.Identity
            var cursor = 0
            val matches = expression.findAll(value).toList()
            if (matches.isEmpty()) {
                return null
            }

            for (match in matches) {
                if (!isTransformSeparator(value.substring(cursor, match.range.first))) {
                    return null
                }
                val name = match.groupValues[1]
                val args = NumberListParser.parse(match.groupValues[2], 6) ?: return null
                val next = transformCommand(name, args) ?: return null
                composed = next.concatenating(composed)
                cursor = match.range.last + 1
            }

            if (!isTransformSeparator(value.substring(cursor))) {
                return null
            }
            return composed
        }

        private fun transformCommand(name: String, args: List<Float>): MathSvgMatrix? {
            return when (name) {
                "translate" -> {
                    if (args.size != 1 && args.size != 2) return null
                    MathSvgMatrix.translation(args[0], if (args.size == 2) args[1] else 0f)
                }
                "scale" -> {
                    if (args.size != 1 && args.size != 2) return null
                    MathSvgMatrix.scale(args[0], if (args.size == 2) args[1] else args[0])
                }
                "matrix" -> {
                    if (args.size != 6) return null
                    MathSvgMatrix.of(args[0], args[1], args[2], args[3], args[4], args[5])
                }
                else -> null
            }
        }

        private fun isTransformSeparator(text: String): Boolean = text.all { it == ',' || it.isWhitespace() }

        private fun localName(name: String): String {
            val separator = name.lastIndexOf(':')
            if (separator < 0) {
                return name
            }
            return name.substring(separator + 1)
        }

        private class PendingText(var value: String, val x: Float, val y: Float, val transform: MathSvgMatrix)

        private companion object {
            const val MAX_ELEMENT_COUNT = 2_048
            const val MAX_PATH_COUNT = 2_048
            const val MAX_DRAW_COMMAND_COUNT = 2_048
            const val MAX_TEXT_COMMAND_COUNT = 128
            const val MAX_ATTRIBUTE_BYTE_COUNT = 64_000
            const val MAX_TEXT_BYTE_COUNT = 8_192
            const val MAX_VIEWBOX_BYTE_COUNT = 128
            const val MAX_TRANSFORM_BYTE_COUNT = 2_048
        }
    }

    /** Thrown to abort SAX parsing immediately once markup is rejected. */
    private object StopParsingException : SAXException("math-svg-rejected") {
        private fun readResolve(): Any = StopParsingException
    }

    /** Parses a list of numbers separated by whitespace and/or single commas, mirroring iOS. */
    private object NumberListParser {
        fun parse(string: String, maximumValueCount: Int = 128): List<Float>? {
            val bytes = string.toByteArray(Charsets.UTF_8)
            val values = ArrayList<Float>()
            var index = skipWhitespace(bytes, 0)
            if (index >= bytes.size) {
                return emptyList()
            }

            while (index < bytes.size) {
                val number = number(bytes, index) ?: return null
                if (values.size >= maximumValueCount) {
                    return null
                }
                values.add(number.value.toFloat())
                index = number.nextIndex

                var consumedComma = false
                var consumedSeparator = false
                while (index < bytes.size) {
                    val byte = bytes[index].toInt() and 0xFF
                    if (isWhitespace(byte)) {
                        consumedSeparator = true
                        index += 1
                    } else if (byte == 44) { // ','
                        if (consumedComma) {
                            return null
                        }
                        consumedComma = true
                        consumedSeparator = true
                        index += 1
                    } else {
                        break
                    }
                }

                if (index == bytes.size) {
                    return if (consumedComma) null else values
                }

                if (!consumedSeparator && !isSign(bytes[index].toInt() and 0xFF)) {
                    return null
                }
            }

            return values
        }

        private data class NumberResult(val value: Double, val nextIndex: Int)

        private fun number(bytes: ByteArray, startIndex: Int): NumberResult? {
            var index = startIndex

            if (index < bytes.size && isSign(bytes[index].toInt() and 0xFF)) {
                index += 1
            }

            var wholeDigits = 0
            while (index < bytes.size && isDigit(bytes[index].toInt() and 0xFF)) {
                wholeDigits += 1
                index += 1
            }

            var fractionDigits = 0
            if (index < bytes.size && (bytes[index].toInt() and 0xFF) == 46) {
                index += 1
                while (index < bytes.size && isDigit(bytes[index].toInt() and 0xFF)) {
                    fractionDigits += 1
                    index += 1
                }
            }

            if (wholeDigits == 0 && fractionDigits == 0) {
                return null
            }

            if (index < bytes.size) {
                val byte = bytes[index].toInt() and 0xFF
                if (byte == 69 || byte == 101) { // 'E' / 'e'
                    index += 1
                    if (index < bytes.size && isSign(bytes[index].toInt() and 0xFF)) {
                        index += 1
                    }
                    var exponentDigits = 0
                    while (index < bytes.size && isDigit(bytes[index].toInt() and 0xFF)) {
                        exponentDigits += 1
                        index += 1
                    }
                    if (exponentDigits == 0) {
                        return null
                    }
                }
            }

            val text = String(bytes, startIndex, index - startIndex, Charsets.UTF_8)
            val value = text.toDoubleOrNull() ?: return null
            if (!value.isFinite()) {
                return null
            }
            return NumberResult(value, index)
        }

        private fun skipWhitespace(bytes: ByteArray, startIndex: Int): Int {
            var index = startIndex
            while (index < bytes.size && isWhitespace(bytes[index].toInt() and 0xFF)) {
                index += 1
            }
            return index
        }

        private fun isWhitespace(byte: Int): Boolean = byte == 32 || byte == 9 || byte == 10 || byte == 12 || byte == 13

        private fun isSign(byte: Int): Boolean = byte == 43 || byte == 45

        private fun isDigit(byte: Int): Boolean = byte in 48..57
    }
}
