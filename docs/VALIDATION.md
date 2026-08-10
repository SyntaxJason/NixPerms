# Validation performed for this phase

The changed Java sources were compiled with Java 21 against the existing universal
artifact and then inserted into a clean copy of that artifact. The finished JAR was
validated with four executable smoke suites:

1. Permission trie: exact rules, nested wildcards, global wildcard and mixed-case input.
2. SQLite core flow: group permissions, user loading, inherited allow and direct deny.
3. Group fan-out: effective-permission event count and immediate visibility after a group edit.
4. Sync and completion: 100 duplicate invalidations coalesced to one rebuild, hierarchical
   suggestions, typo correction and reflected Bukkit permission discovery.

The final JAR also passed a ZIP integrity check.

A full Maven reactor run was not available in the execution environment because Maven was
not installed. The added JUnit tests are included for the normal `mvn clean verify` pipeline.
