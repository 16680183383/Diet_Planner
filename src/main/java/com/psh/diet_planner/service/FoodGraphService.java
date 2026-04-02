package com.psh.diet_planner.service;

import com.psh.diet_planner.dto.CreativeCookingRequest;
import com.psh.diet_planner.dto.CreativeCookingResponse;
import com.psh.diet_planner.dto.FoodPairingRequest;
import com.psh.diet_planner.dto.PairingInsightResponse;
import com.psh.diet_planner.dto.PairingResponse;
import com.psh.diet_planner.dto.SafetyCheckRequest;
import com.psh.diet_planner.dto.SafetyCheckResponse;
import com.psh.diet_planner.dto.SmartMealPlanRequest;
import com.psh.diet_planner.dto.SmartMealPlanResponse;
import java.util.List;

public interface FoodGraphService {

    PairingResponse recommendPairings(FoodPairingRequest request);

    PairingInsightResponse recommendPairingsWithReason(FoodPairingRequest request);

    SmartMealPlanResponse generateMealPlan(SmartMealPlanRequest request);

    SafetyCheckResponse checkSafety(SafetyCheckRequest request);

    CreativeCookingResponse createCreativeIdea(CreativeCookingRequest request);
}
