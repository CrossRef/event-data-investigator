# Event Data Investigator

**If you find something missing from Crossref Event Data, [report missing data over here](https://github.com/CrossRef/event-data-enquiries/issues).**

Continual quality improvement tool. Take data from various places, answer support queries, and feed back into the Event Data system.

## What does it do?

This is a continuous feedback tool and will take data from all kinds of places, including humans and machines. It will perform the following functions:

 - Interface with GitHub issues to automatically identify missing Events, new sitemaps, RSS feeds etc (TODO)
 - One-off patches to Events when we need to update them.
 - Ingestion of DOI resolution logs to identify new RSS feeds, sitemaps etc (TODO)
 - Ingestion of Publisher server logs to identify new RSS feeds, sitemaps etc (TODO)

### Patch

Sometimes we need to patch Events. The code involved in the updates will be stored here for future reference. Because they are one-offs, they are run by hand.

## Config

The following environment variables are expected:

 - `GLOBAL_JWT_SECRETS`

## License

Copyright © 2018 Crossref

Distributed under the The MIT License (MIT)
