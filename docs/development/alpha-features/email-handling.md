# Email Domain Handling

Considered email domain stuff at transform level, but decided it's probably top-down; changing in dozens of places is too much.


### add an `EMAIL_DOMAINS_TO_PRESERVE` config parameter?

use case would be to just add all internal domains to the list, as know those are safe; gives easy internal vs external distinction,
if nothing else - with no need to match to HRIS at all.

