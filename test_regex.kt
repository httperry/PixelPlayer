fun main() {
    val js = "var a=1;oL5=function(y){return y;};var b=2;"
    val modifiedJs = js.replace(
        Regex("""([;{,\n\s])(${Regex.escape("oL5")}=function\()"""),
        "$1window.nsigWrapper=$2"
    )
    println(modifiedJs)
}
