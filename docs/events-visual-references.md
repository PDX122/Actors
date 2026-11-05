# ICBM visual references

The current Events library model is extracted from the user-supplied
`icbm-model.zip`, with three stages, four fins, split shroud and post-boost bus.
The Titan II research below records the retired model design. The references guide exterior appearance and
animation only. No reference photographs are bundled as game assets.

## Reviewed photographs

- [Titan II N-6 launch, 11 July 1962](https://ccspacemuseum.org/wp-content/uploads/missles/Titan/Titan_II_620711_0032.jpg): silhouette, tank proportions and launch cloud.
- [Titan II N-20 launch, 29 May 1963](https://ccspacemuseum.org/wp-content/uploads/missles/Titan/Titan_II_630529_0023.jpg): stage joints, plume brightness and accelerating departure.
- [Titan II N-31 launch, 16 January 1964](https://ccspacemuseum.org/wp-content/uploads/missles/Titan/Titan_II_640116_0008.jpg): finless body and upper-stage proportions.
- [Titan Missile Museum engine underside](https://titanmissilemuseum.org/wp-content/uploads/2020/01/titan-missile-gallery-10.jpg): twin hollow engine bells and exposed lower structure.
- [Titan Missile Museum silo view](https://titanmissilemuseum.org/wp-content/uploads/2020/01/titan-missile-gallery-13.jpg): dark nose fairing, rounded tip, metal collar and weathering.
- [Titan Missile Museum re-entry shell exhibit](https://titanmissilemuseum.org/wp-content/uploads/2020/01/titan-missile-gallery-15.jpg): blunt nose and surface finish.

Also reviewed the six relevant user-supplied references: the metal-body launch
photograph, silo nose close-up, mobile canister, flight-phase illustration,
ballistic-arc diagram, and separated-stage exhibit diagram. The mobile canister
is a different design, so its transport tube is not used as the flying missile.

The [Cape Canaveral museum archive](https://ccspacemuseum.org/galleries/titan/)
identifies the launch photographs. The
[Titan Missile Museum gallery](https://titanmissilemuseum.org/titan-missile-museum-gallery/)
provides the engine and silo photographs. Its
[history and specifications](https://titanmissilemuseum.org/about/titan-ii-history/)
give the full-size silhouette proportions and distinguish powered flight from
ballistic coast.

## Implementation decisions

- A roughly 31-metre body with two stages, an ablative nose, metal tank panels,
  raised cable raceways, collars, access hatches, seams, rivets and exposed bells.
- The actual world-event shader also renders the library's isolated 3D turntable
  and sequence preview, preventing a different preview model from drifting apart.
- Ignition builds into accelerating ascent. The booster retains its release
  velocity, tumbles and falls. The upper stage burns briefly, then cuts off.
  Fairing halves open before the separate re-entry vehicle descends.
- Exhaust is confined to powered stages. Atmospheric re-entry gets a bow shock,
  incandescent nose and ablative wake. The launch leaves a lingering smoke trail.
- New events run for 12 seconds by default, with impact at 7.2 seconds. This is
  deliberately compressed cinematic timing, not real flight duration or range.
  Stretching the clip rescales the complete sequence; existing saved durations
  remain unchanged.

## Nuclear cloud update

The user's two cloud references guided the thick stem, broad rolling cap, bright
underside, condensation skirts and dust base. LLNL NARAC's historical-film/cloud-rise
comparisons informed the visual sequence:
https://narac.llnl.gov/research-and-development/nuclear-fallout-modeling
This implementation uses analytic volumes and advected noise, not NARAC physics.

Model simplification uses the authoring-only meshoptimizer attribute metric:
https://github.com/zeux/meshoptimizer#simplification
Runtime has no meshoptimizer dependency. The 7,000-triangle mesh retains source
UV seams and material attributes; original source has 40,272 triangles.
