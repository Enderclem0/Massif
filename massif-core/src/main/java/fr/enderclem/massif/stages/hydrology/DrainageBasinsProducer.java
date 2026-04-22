package fr.enderclem.massif.stages.hydrology;

import fr.enderclem.massif.api.DrainageBasin;
import fr.enderclem.massif.api.DrainageBasins;
import fr.enderclem.massif.api.DrainageGraph;
import fr.enderclem.massif.api.DrainageGraph.CellDrainage;
import fr.enderclem.massif.api.MassifKeys;
import fr.enderclem.massif.blackboard.FeatureKey;
import fr.enderclem.massif.pipeline.ExecutionContext;
import fr.enderclem.massif.pipeline.Producer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives {@link DrainageBasins} from {@link DrainageGraph} by BFS from
 * each terminal through the reversed downhill graph. Every non-terminal
 * cell follows its downhill pointer to exactly one terminal, so the
 * basins partition the cell set.
 */
public final class DrainageBasinsProducer implements Producer {

    @Override
    public String name() {
        return "hydrology.drainage_basins";
    }

    @Override
    public Set<FeatureKey<?>> writes() {
        return Set.of(MassifKeys.DRAINAGE_BASINS);
    }

    @Override
    public Set<FeatureKey<?>> reads() {
        return Set.of(MassifKeys.DRAINAGE_GRAPH);
    }

    @Override
    public void compute(ExecutionContext ctx) {
        DrainageGraph drainage = ctx.read(MassifKeys.DRAINAGE_GRAPH);

        // Build reverse adjacency: for each cell, who flows INTO it.
        Map<Integer, List<Integer>> upstream = new HashMap<>();
        for (CellDrainage c : drainage.byCellId().values()) {
            if (c.downhillCellId() < 0) continue;
            upstream.computeIfAbsent(c.downhillCellId(), k -> new ArrayList<>()).add(c.cellId());
        }
        // Stable order for BFS expansion.
        for (List<Integer> ups : upstream.values()) ups.sort(Comparator.naturalOrder());

        List<DrainageBasin> basins = new ArrayList<>(drainage.terminals().size());
        Set<Integer> assigned = new HashSet<>();
        List<Integer> orderedTerminals = new ArrayList<>(drainage.terminals());
        orderedTerminals.sort(Comparator.naturalOrder());
        for (int terminalId : orderedTerminals) {
            if (!assigned.add(terminalId)) continue; // defensive
            List<Integer> members = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(terminalId);
            members.add(terminalId);
            while (!queue.isEmpty()) {
                int cur = queue.pollFirst();
                List<Integer> ups = upstream.get(cur);
                if (ups == null) continue;
                for (int up : ups) {
                    if (!assigned.add(up)) continue;
                    members.add(up);
                    queue.addLast(up);
                }
            }
            CellDrainage terminal = drainage.of(terminalId);
            basins.add(new DrainageBasin(terminalId, terminal.isEndorheicTerminal(), members));
        }

        ctx.write(MassifKeys.DRAINAGE_BASINS, new DrainageBasins(basins));
    }
}
