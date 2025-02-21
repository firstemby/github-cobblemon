
### Additions
- Added support for NPCs to use Pokémon models and vice versa. This will require changes in addons that add fossil types due to naming conflicts between fossils and the Pokémon species.
- Added `/changejointscale` command to change the scale of a joint in a model. Unbelievably funny.

### Developer
- Removed all VaryingModelRepository subclasses into the parent class. 

### Addons
- Added `transformedParts` to the root of poser JSONs so it now exists in both poses and the model overall. 