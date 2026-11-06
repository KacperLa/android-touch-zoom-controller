#!/usr/bin/env python3
"""Source-wiring regression guard, NOT Android compositor/runtime validation."""
from pathlib import Path
import re
source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/io/github/kacperla/touchzoom/ZoomService.java').read_text()
# Ignore comments so a rationale cannot satisfy the executable wiring assertion.
source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
setup = source.split('private void createMenuOverlay(){', 1)[1].split('private void hideMenuSurface(){', 1)[0]
hide = source.split('private void hideMenuSurface(){', 1)[1].split('private void dismissControllerMenu(){', 1)[0]
compact = lambda text: re.sub(r'\s+', '', text)
setup, hide = compact(setup), compact(hide)
assert '.reparent(menuPackage.getSurfaceControl(),menuControl)' in setup, 'Package must remain under the menu parent'
assert '.setVisibility(menuPackage.getSurfaceControl(),true)' in setup, 'SCVH package starts hidden: explicitly show the inner root'
assert '.setVisibility(menuControl,false)' in setup, 'Menu must start hidden behind its parent gate'
assert '.setVisibility(menuControl,false)' in hide, 'Dismissal must hide parent input subtree, not only make it transparent'
assert 'setAlpha(' not in hide, 'Alpha-only dismissal can leave invisible input interception'
print('5 menu surface source-wiring checks passed (not a device test)')
