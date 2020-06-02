# evaluator

To build the distribution run 

``
./gradlew distTar
``

or 

``
./gradlew distZip
``

To create a distribution for local testing run

``
./gradlew installDist
``

You can then test the distribution for extracting product fields:

````
cd build/install/evaluator
echo 'https://www.mountainhardwear.com/mens-finder-rain-jacket-1572331.html?cgid=mens-jackets-rain&dwvar_1572331_variationColor=492#start=2' > urls.txt
echo 'https://www.mountainhardwear.com/womens-stretchdown-jacket-1756291.html?cgid=womens-jackets-insulated&dwvar_1756291_variationColor=010#start=1' >> urls.txt
./bin/evaluator urls.txt ../../../src/test/resources/test_template.yaml ~/HTMLCacheDir product
````

which should output
````
url title
https://www.mountainhardwear.com/mens-finder-rain-jacket-1572331.html?cgid=mens-jackets-rain&dwvar_1572331_variationColor=492#start=2    Men's Finder™ Rain Jacket 
https://www.mountainhardwear.com/womens-stretchdown-jacket-1756291.html?cgid=womens-jackets-insulated&dwvar_1756291_variationColor=010#start=1   Women's StretchDown™ Jacket 
````

Test for extracting general fields:
````
cd build/install/evaluator
echo 'https://therighthairstyles.com/haircut-terms/' > urls.txt
./bin/evaluator urls.txt ../../../src/test/resources/general_template.yaml ~/HTMLCacheDir general
````

which should output
````
url	publish_datetime
https://therighthairstyles.com/haircut-terms/	May 29, 2019
````

If the above failed because the request timeout or access denied. Then do the following:

Open a test link in browser, download raw html into a local file (e.g. test.html), then
run with raw_html option on:
````
./bin/evaluator raw_html test.html ../../../src/test/resources/test_template.yaml ~/HTMLCacheDir product
````

which should output
````
url	title
test.html	Men's Monkey Man/2™ Jacket
````

The raw_html option can also handle a directory as its 2nd argument. In this case, the evaluator processes all the files in the directory using the same template file (3rd argument). Results are shown one file per line. The file name is shown in the first position (url column).


Note that the HTML contents of each URL are cached on disk, in the folder specified by the next parameter (~/HTMLCacheDir). 
When the above command is repeated, the execution is noticeably faster, because of the cache hit. However, this does not apply to raw_html queries.

