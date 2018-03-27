# Event Data Investigator

**If you find something missing from Crossref Event Data, [report missing data over here](https://github.com/CrossRef/event-data-enquiries/issues).**

Continual quality improvement tool. Take data from various places, answer support queries, and feed back into the Event Data system.

# What does it do?

This is a continuous feedback tool and will take data from all kinds of places, including humans and machines. It will perform the following functions:

 - Interface with GitHub issues to automatically identify missing Events, new sitemaps, RSS feeds etc (TODO)
 - One-off patches to Events when we need to update them.
 - Ingestion of DOI resolution logs to identify new RSS feeds, sitemaps etc (TODO)
 - Ingestion of Publisher server logs to identify new RSS feeds, sitemaps etc (TODO)

## Patch

Sometimes we need to patch Events. The code involved in the updates will be stored here for future reference. Because they are one-offs, they are run by hand.

## Integrity Checks

The Investigator runs integrity checks on a schedule and reports any errors.

### Query vs Archive

The `query-vs-archive` check looks at all of the Event IDs in the Event Bus Archive for a given day. It compares them to the Query API. It does this day by day. Any Event IDs missing from the Query API are reported.

### Archive vs Query

The `archive-vs-query` check looks at all of the Event IDs in the Query API for a given day. It compares them to the Event Bus Archive. It does this day by day. Any Event IDs missing from the Archive are reported.

### Missing Evidence Log

The `evidence-log-present` checks that the Evidence Log file is available for a given day in JSON and CSV format.

## Running

Run scheduled checks:

    time docker-compose -f docker-compose-local.yml run -w /usr/src/app investigator lein run scheduled-checks

Running locally:

    time docker-compose -f docker-compose-local.yml run -w /usr/src/app investigator lein repl

## Config

The following environment variables are expected:

 - `GLOBAL_JWT_SECRETS`
 - `INVESTIGATOR_GITHUB_TOKEN`
 - `GLOBAL_EVENT_BUS_BASE`
 - `GLOBAL_QUERY_BUS_BASE`
 - `GLOBAL_EVENT_BUS_BASE`
 - `QUERY_PREFIX_WHITELIST_ARTIFACT_NAME`
 - `QUERY_WHITELIST_ARTIFACT_NAME`

In order to authenticate with GitHub, an access token with the 'repo' scope is required. The `crossref-support` account is used.

## License

Copyright © 2018 Crossref

Distributed under the The MIT License (MIT)
