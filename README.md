# openunison-test-harness
Maven project for automating the testing of OpenUnison provisioning targets

Fork and copy this project to develop a locally.  You'll need at least Java 21.  Once downloaded, you can run a test case by running

```
$ mvn clean test
```

With no changes, you should see logs with this at the end:

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.876 s -- in io.openunison.provisioning.customtarget.CustomTargetTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  10.162 s
[INFO] Finished at: 2026-01-13T15:18:58-05:00
[INFO] ------------------------------------------------------------------------
```

The example target just stores users in an internal JSON database in `src/test/yaml/custom-target.yaml`

For details on how to create your custom target, see https://openunison.github.io/customization/custom_provisioning_targets/
