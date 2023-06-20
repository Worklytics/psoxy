# example-lib

Components intended to be re-used in many examples.

Problem trying to solve is that MANY examples have the sample variables and even



## Implementation

### Hardlinks
Hardlink from examples to files in this directory, to re-use terraform without duplicating
the files.

Pros:
  - seems to work?

Cons:
  - intellij doesn't seem to recognize/differentiate them from plain files; nothing obvious
    when someone opens file in a given example that they're altering a file that is linked
    from more locations
  - unclear that it will play nicely across OSes, and some notes that can fail in unexpected
    ways after cloning/branching (as inodes can differ)


### Sync script
Alternatively, something we run that keeps files here in sync with all examples in which they
are used?


### Dismissed
  - softlinks - terraform doesn't like them

## Alternative
  - An über-example that includes all sources + hosts, so now need to repeat source-specific
    terraform in multiple places. But `terraform validate`/etc wouldn't work on such an example,
    bc host stuff would conflict?? Or if didn't, what we'd be testing with that validate wouldn't be
    that comparable to real world use of the example
  - A script that builds example from `lib/` of terraform files that correspond to each component,
    rather than having prebuilt examples.  OK, but complicates testing as you need to run that
    script and THEN `terraform init/validate/etc` to test it.


