package com.amazon.amazonpaymerchanturl.model;

import com.amazon.urlvendorreviewmodel.model.EvidenceSpec;
import com.amazon.urlvendorreviewmodel.type.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Inspection {
    private String inspectionType;
    private String inspectionTypeName;
    private String inspectionCategory;
    private String inspectionCategoryName;
    private RiskLevel riskLevel;
    private List<EvidenceSpec> evidences;
}
