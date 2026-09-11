package com.example.ai01.service.batch;

import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.model.RuleProjectMatch;
import java.util.List;
public interface RuleBatchingStrategy {

    List<RuleCheckBatch> buildBatches(
            List<RuleProjectMatch> matches
    );
}
