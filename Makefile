update: setup
	jekyll

package: update
	zip -rq website _site

setup:
	`which jekyll >/dev/null 2>/dev/null` || sudo gem install jekyll

clean:
	rm -rf _site website.tar.bz2

