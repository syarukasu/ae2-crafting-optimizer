# Public API baseline

`aco-v3-public-abi.tsv` was generated from the compiled Forge 1.20.1 classes at
ACO commit `dda32ad`, before the Issue #218 redesign. That commit is the local
2.0.0-rc.6-rc4-vm.7-dev.3 baseline, not a claim of a published binary comparison.

The 1,182 sorted rows cover exported API types and the local types referenced by
their public/protected methods, fields, superclass, interfaces and signatures.
`PublicApiBinaryCompatibilityTest` rejects removal or mutation of these rows.
Additive methods/types are allowed. This is an ABI guard, not proof of behavior
or execution compatibility with every add-on. Do not regenerate this fixture to
hide an intentional or accidental API break.
