val RE_PLAYER_VERSION = Regex("player\\\\?/([a-zA-Z0-9_-]+)\\\\?/")
val text = "player\\/8456c9de\\/www-widgetapi.vflset\\/www-widgetapi.js"
val match = RE_PLAYER_VERSION.find(text)
println("Match: \${match?.groupValues?.get(1)}")

val text2 = "player/8456c9de/www-widgetapi.vflset/www-widgetapi.js"
val match2 = RE_PLAYER_VERSION.find(text2)
println("Match2: \${match2?.groupValues?.get(1)}")
