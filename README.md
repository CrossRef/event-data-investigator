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

### Archive / Query Integrity

These two checks look at all of the Event IDs in the Event Bus Archive for a given day. It compares them to the Query API. It does this day by day. Any Event IDs missing from the Query API are reported.

### Missing Evidence Log

The daily Evidence Log dumps in CSV and JSON format are checked daily.

### Evidence Record Snapshot

This check ensures that the daily snapshot of input Evidence Records exists. 

## Running

Run scheduled checks:

    time docker-compose -f docker-compose.yml run -w /usr/src/app investigator lein run scheduled-checks

Running locally:

    time docker-compose -f docker-compose.yml run -w /usr/src/app investigator lein repl

## Config

The following environment variables are expected:

 - `GLOBAL_JWT_SECRETS`
 - `INVESTIGATOR_GITHUB_TOKEN`
 - `GLOBAL_QUERY_BASE`
 - `GLOBAL_EVENT_BUS_BASE`
 - `QUERY_PREFIX_WHITELIST_ARTIFACT_NAME`
 - `QUERY_WHITELIST_ARTIFACT_NAME`
 - `STATUS_SNAPSHOT_S3_KEY`
 - `STATUS_SNAPSHOT_S3_SECRET`
 - `STATUS_SNAPSHOT_S3_REGION_NAME`
 - `STATUS_SNAPSHOT_S3_BUCKET_NAME`
 - `TWITTER_PASWORD`
 - `TWITTER_API_KEY`
 - `TWITTER_API_SECRET`
 - `TWITTER_ACCESS_TOKEN`
 - `TWITTER_ACCESS_TOKEN_SECRET`
 - `PERCOLATOR_EVIDENCE_BUCKET_NAME` - access to Evidence Record bucket for patching.
 - `PERCOLATOR_EVIDENCE_REGION_NAME`
 - `PERCOLATOR_EVIDENCE_STORAGE`
 - `PERCOLATOR_S3_KEY`
 - `PERCOLATOR_S3_SECRET`
 - `GLOBAL_EVIDENCE_URL_BASE` e.g. https://evidence.eventdata.crossref.org


In order to authenticate with GitHub, an access token with the 'repo' scope is required. The `crossref-support` account is used.

Twitter details come from dashboard at https://apps.twitter.com/ .

## Test

  time docker-compose -f docker-compose.yml run -w /usr/src/app investigator lein test

## Tools

For use locally.

### Kafka Evidence Records recovery

For use on Kafka data files when there's a serious crash and we want to check over the remaining log files from Kafka's data directory. This tool ingests the Evidence Record input topic and checks with the Evidence Registry to see if each Evidence Record was processed. If not, it will save each one to an output file. Because of file access, easier not to run in a Docker container.

Example usage:

    
    PERCOLATOR_EVIDENCE_BUCKET_NAME=XXX PERCOLATOR_EVIDENCE_REGION_NAME=XXX PERCOLATOR_S3_KEY=XXX PERCOLATOR_S3_SECRET=XXX time lein run check-kafka-logs-evidence-records ~/data/kafka-rescue-2018-07-24/kafka1/data/ /tmp/kafka-rescue-output hetzner-percolator-input


### Kafka Evidence Logs recovery

For use on Kafka data files when there's a serious crash and we want to check over the remaining log files from Kafka's data directory. This tool ingests the Evidence Log input and writes it all, as newline-separated JSON objects, to an output file.

Example usage:

    
    PERCOLATOR_EVIDENCE_BUCKET_NAME=XXX PERCOLATOR_EVIDENCE_REGION_NAME=XXX PERCOLATOR_S3_KEY=XXX PERCOLATOR_S3_SECRET=XXX time lein run check-kafka-logs-evidence-log ~/data/kafka-rescue-2018-07-24/kafka1/data/ /tmp/kafka-rescue-output/evidencelog.ndjson hetzner-status


## License

Copyright © 2018 Crossref

Distributed under the The MIT License (MIT)
