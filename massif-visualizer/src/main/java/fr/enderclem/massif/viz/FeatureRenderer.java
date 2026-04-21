package fr.enderclem.massif.viz;

import fr.enderclem.massif.api.RegionPlan;
import fr.enderclem.massif.blackboard.FeatureKey;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Turns one feature from a {@link RegionPlan} into a {@link BufferedImage}.
 *
 * Two-phase API: callers first call {@link #computeScale} with every plan that
 * will be rendered in the current view, then pass the returned scale token to
 * each {@link #render} call. This lets renderers establish a common colour
 * mapping (e.g. a shared min/max for a float grid) so adjacent regions don't
 * get independent normalisations and produce visible seams.
 */
public interface FeatureRenderer {

    FeatureKey<?> key();

    String label();

    /** Compute shared state from all plans in the view. Return value is opaque. */
    default Object computeScale(List<RegionPlan> plans) {
        return null;
    }

    BufferedImage render(RegionPlan plan, Object scale);

    /** Optional legend entries for the current plan. Default: none. */
    default List<LegendEntry> legend(RegionPlan plan) {
        return List.of();
    }

    record LegendEntry(String label, Color color) {}
}
