# Prose style: comments and logs

How this codebase writes to humans (and what gives it away when it doesn't).

## The rule

Explain the **why**, once, at the length the confusion warrants. A reader
should never be able to guess the comment's shape before reading it.

## Comment cadence

- **One line** for anything a competent reader gets in five seconds.
  `// Null kinds never equal a non-empty needle, so no special case.`
- **A short paragraph** for a decision with one reason.
- **A DO-NOT-TOUCH marker** for load-bearing invariants: what breaks, addressed
  to whoever is about to break it. `// Clamp. NaN anywhere here ruins every
  position downstream. Don't touch.` This replaces the essay, not the
  knowledge: the full story belongs in git history.
- **Never the same rhetorical shape twice in one file.** If every method ends
  with "The rejected alternative was X", no individual explanation is being
  read: the shape is. Vary: sometimes lead with the consequence, sometimes
  with the failure it prevents, sometimes with a single example.
- **No throat-clearing.** Cut "It is worth noting that", "It is important to
  understand", "This exists because" (just say the thing), "Note that".
- **Numbers over adjectives.** "One line per actor per five seconds" beats
  "excessive logging". Name the tick, the distance, the cap.
- **Javadoc `{@link}` only where navigation helps.** A wall of links to
  things named in the same sentence is decoration.

## Bad (real pattern, anonymized)

```java
/**
 * Puts the X onto the Y.
 *
 * <p>This is the only place that does Z, and that is the whole design. A
 * take that stored W threw away V, and an earlier version compensated with
 * U, which had never seen T and so undid exactly the S whose outcome
 * depended on R. An earlier version also did Q, which P.
 *
 * <p>The rejected alternative was O, which N. It was rejected because M.
 */
```

Three paragraphs to say "this writes X to Y; O doesn't because M". The
reader stops reading these after the third one, including the one time it
mattered.

## Good

```java
/** Writes the recorded X onto the Y. O can't: M. */
```

Same information a maintainer needs on a normal day. The war story belongs
in git history, or in exactly one place per subsystem, not on every method.

## Log voice

- Terse and specific: what happened, to whom, on which tick.
  `"Actor '{}' re-fired {} arrow(s) for take tick {}; treating it as a scrub"`
- No passive-verb-plus-colon templates: not `"Failed to fire Z for {}: {}"`,
  but `"Actor '{}' tried to fire and dropped it: {}"`.
- No marketing adjectives anywhere: nothing is robust, seamless, or elegant.
- Numbers, not adverbs: `"off by {} blocks, jumping {}"`, never "slightly off".
- One line per event. If it needs two sentences, it needs a design doc.

## The test

Read the comment out loud. If it sounds like documentation, shorten it. If it
sounds like the person who got burned explaining what not to touch, keep it.
