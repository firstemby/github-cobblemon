## Form Change Effects
A forme change done for a temporary form (mega evolution, Cherrim, Wishiwashi) will run an action effect to perform the
transition. This can take as long as it needs, as battles will wait until it is complete before letting the turn proceed.

### Choosing Which Action Effect To Use 
Which action effect is used for which form is something you control. The system will first check to see if the form, 
defined inside a species JSON, mentions an `actionEffectIdentifier`. This could be `cobblemon:mega_x_transformation`,
for example. If it has, then it will attempt to load that action effect and error if it does not exist. If it hasn't
specified an `actionEffectIdentifier`, then it will try finding one for `cobblemon:form_{form name}`, such as
`cobblemon:form_school`. If that also fails, then it will use `cobblemon:generic_form`, which you can find in 
`resources/data/cobblemon/action_effects/forms/` folder of the mod.

Forms can also define a transition for when you are moving from a temporary form back into a permanent form. Wishiwashi
in its School form is temporary, its Solo form is permanent. If you wanted a special effect for when it is reverting,
the logic is the similar as above but with `_revert` at the end of everything. In other words, if you specified
`actionEffectIdentifier`, then it will look to see if there is one with the same name but with `_revert` on the end. If
it doesn't find it, it won't error like the above case, but instead look for `cobblemon:form_{form name}_revert`, such
as `cobblemon:form_school_revert`, and failing that, `cobblemon:generic_form_revert`.

### Timing The Swap
At some point during the effect, you're going to want the form of the Pokémon to swap over instead of just playing the
effects. The swap will occur at whichever point happens first: the `"transformation"` hold is removed from the action
effect context, or the action effect completes. If you make it happen earlier, the action effect will continue playing
even though the switch has occurred.

If you review `generic_form.json` in the `action_effects` data folder, you'll see that it adds the `"transformation"`
hold first thing, and then removes it at a particular timing. This will make the model change over at that moment. This
is identical in execution to the `"effects"` hold used in move action effects.

### Some of the effect JSONs that are in the mod:

`generic_form.json`
```json
{
  "timeline": [
    {
      "type": "add_holds",
      "holds": ["transformation"]
    },
    {
      "type": "entity_particles",
      "effect": "cobblemon:statup_actor",
      "delay": 0.1,
      "locators": ["root"]
    },
    {
      "type": "pause",
      "pause": 1.0
    },
    {
      "type": "remove_holds",
      "holds": ["transformation"]
    },
    {
      "type": "pause",
      "pause": 2.0
    }
  ]
}
```

`generic_form_revert.json`
```json
{
  "timeline": [
    {
      "type": "add_holds",
      "holds": ["transformation"]
    },
    {
      "type": "entity_particles",
      "effect": "cobblemon:statdown_actor",
      "delay": 0.1,
      "locators": ["root"]
    },
    {
      "type": "pause",
      "pause": 1.0
    },
    {
      "type": "remove_holds",
      "holds": ["transformation"]
    },
    {
      "type": "pause",
      "pause": 2.0
    }
  ]
}
```