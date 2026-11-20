package edu.unicauca.aplimovil.triageu.help

/** Respuestas locales por palabras clave; funciona sin conexión. */
class LocalHelpAgent : HelpAgent {

    /** Mejor tema que coincide con la pregunta, o null si no hay coincidencia. */
    fun match(question: String): HelpAnswer? {
        val q = normalize(question)
        if (q.isBlank()) return null
        val best = HelpTopics.all
            .map { topic -> topic to topic.keywords.count { q.contains(it) } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first ?: return null
        val steps = best.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        return HelpAnswer(
            text = "$steps\n\nPuedes ver los pasos en: ${best.title}",
            topicId = best.id
        )
    }

    override suspend fun ask(question: String, online: Boolean, pending: Int): HelpAnswer {
        match(question)?.let { return it }
        return HelpAnswer(
            "No estoy seguro de eso. Puedo ayudarte con estos temas: " +
                HelpTopics.all.joinToString(", ") { it.title } + "."
        )
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
        .replace('ü', 'u').replace('ñ', 'n')
}
