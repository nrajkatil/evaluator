
const puppeteer = require('puppeteer');

(async () => {
 var myArgs = process.argv.slice(2);
 var url = myArgs[0]
 var userAgent = myArgs[1]
 const browser = await puppeteer.launch();
 const page = await browser.newPage();
 await page.setUserAgent(userAgent);
 await page.goto(url);
 var HTML = await page.content();
 console.log(HTML);

 await browser.close();
})();
