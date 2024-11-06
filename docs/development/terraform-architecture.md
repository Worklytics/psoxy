# Approaches for Example / Module design

The problem we're trying to solve is that various features, such as VPCs/etc, are relevant to a
small set of users. It would complicate the usual cases to enable them for all cases. So we need to
provide easy support for extending the examples/modules to support them in the extreme cases.

Composition is the canonical terraform approach.

Two approaches:

1. composition, which is canonical terraform

    a. commented out
    - validation
    - instructions to explain to customers are more complex

    b. conditional: validation will work, but hacky 0 indexes around in places

2. conditionals + variables

    pros:
    - simplest for customers
    - easiest to read/follow

   cons:
     - verbose interfaces
     - brittle stacks (changing variable requires changing many in hierarchy)
