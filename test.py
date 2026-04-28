import re
js = open("base.js").read()
# Find an object with methods taking (a,b) that do splice/reverse/a[0]=...
r = re.finditer(r"var ([a-zA-Z0-9_\$]{2,})=\{([a-zA-Z0-9_\$]+:function\(a,b\)\{.+?\})+(,\s*[a-zA-Z0-9_\$]+:function\(a,b\)\{.+?\})*\};", js)
for m in list(r)[:5]:
    print("Match O:", m.group(0)[:150])
