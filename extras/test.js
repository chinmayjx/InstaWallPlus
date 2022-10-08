const fs = require('fs')
fs.writeFileSync("abc.txt", "hello")
fs.appendFileSync("abc.txt", " world\n")

