package com.example.experiment_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO = Data Transfer Object: the JSON box that goes IN and OUT of the API.
 * The database class (Experiment) never leaves the service; this class does.
 *
 * Why so many @JsonAlias? The old React app and the old Flexsim scripts used several spellings
 * for the same field (flexibleResources, flexible_resources, flexibleresources ...).
 * An alias means "also accept this name when reading JSON". Nothing breaks for old callers.
 *
 * @Size(max = ...) = if a text is longer than the database column, answer 400 (Bad Request)
 * instead of a database error (500).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentDto {

    public ExperimentDto() {
    }

    // The id is made by the database. The client can NOT set it (READ_ONLY = ignored on input).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long experimentId;

    @Size(max = 255)
    private String experimentName;

    @Size(max = 255)
    private String terminal;

    @Size(max = 255)
    private String sortingType;

    // If missing, the service uses 0 (same as the old app).
    private Integer useCaseId;

    // Format yyyy-MM-dd, for example 2026-10-19.
    private LocalDate selectedDate;

    // Shift times. We accept BOTH '23:00:00' and '2026-10-19T23:00:00'.
    // They are text here on purpose; ExperimentMapper turns them into date-times.
    // We always answer with the full form 2026-10-19T23:00:00.
    private String startTime;

    private String endTime;

    private Integer parcelCount;

    private Integer newParcelCount;

    private Integer parcelChangeValue;

    // increase / decrease
    @Size(max = 255)
    private String parcelChangeMode;

    private Integer maxRecirculationCount;

    // The old app used the lower case name 'initialrunspeed' in JSON. We keep it, and also accept the other spellings.
    @JsonProperty("initialrunspeed")
    @JsonAlias({"initialRunSpeed", "initial_run_speed"})
    private Integer initialRunSpeed;

    @JsonAlias({"flexibleResources", "flexible_resources", "flexibleresources"})
    private Integer flexibleResources;

    @JsonAlias({"phoneBoomChute", "phone_boom_chute", "PHONE_BOOM_CHUTE", "phoneboomchute"})
    private Integer phoneBoomChute;

    @JsonAlias({"dropChuteFlexible", "drop_chute_flexible", "DROP_CHUTE_FLEXIBLE", "dropchuteflexible"})
    private Integer dropChuteFlexible;

    @JsonAlias({"flexibleUnloadResources", "flexible_unload_resources", "FLEXIBLE_UNLOAD_RESOURCES", "flexibleunloadresources"})
    private Integer flexibleUnloadResources;

    @JsonAlias({"sorterFullPercent", "sorter_full_percent", "SORTER_FULL_PERCENT", "sorterfullpercent"})
    private Integer sorterFullPercent;

    @JsonAlias({"minimumCageOccupancy", "minimum_cage_occupancy", "Minimum_Cage_Occupancy", "MINIMUM_CAGE_OCCUPANCY"})
    private Integer minimumCageOccupancy;

    @JsonAlias({"maximumCageOccupancy", "maximum_cage_occupancy", "Maximum_Cage_Occupancy", "MAXIMUM_CAGE_OCCUPANCY"})
    private Integer maximumCageOccupancy;

    @Size(max = 30)
    @JsonAlias({"guiMode", "gui_mode", "GUI_MODE", "simulationGuiMode"})
    private String guiMode;

    // The old app used the lower case name 'allsortedat'.
    @Size(max = 20)
    @JsonProperty("allsortedat")
    @JsonAlias({"allSortedAt", "all_sorted_at"})
    private String allSortedAt;

    // Output only: the owner's name (copy stored in experiment.owner_username).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String username;

    // Output only, in UTC.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    // Output only: PENDING, RUNNING, TERMINATED, COMPLETED or FAILED.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String status;

    // The old app also sent these lower case copies in its answers. We keep them so the React app
    // keeps working whichever spelling it reads. (We can remove the copies later, in the frontend step.)
    @JsonGetter("flexibleresources")
    public Integer getFlexibleResourcesSerialized() {
        return flexibleResources;
    }

    @JsonGetter("phoneboomchute")
    public Integer getPhoneBoomChuteSerialized() {
        return phoneBoomChute;
    }

    @JsonGetter("dropchuteflexible")
    public Integer getDropChuteFlexibleSerialized() {
        return dropChuteFlexible;
    }

    @JsonGetter("flexibleunloadresources")
    public Integer getFlexibleUnloadResourcesSerialized() {
        return flexibleUnloadResources;
    }

    @JsonGetter("sorterfullpercent")
    public Integer getSorterFullPercentSerialized() {
        return sorterFullPercent;
    }

    public Long getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getTerminal() {
        return terminal;
    }

    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }

    public String getSortingType() {
        return sortingType;
    }

    public void setSortingType(String sortingType) {
        this.sortingType = sortingType;
    }

    public Integer getUseCaseId() {
        return useCaseId;
    }

    public void setUseCaseId(Integer useCaseId) {
        this.useCaseId = useCaseId;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getParcelCount() {
        return parcelCount;
    }

    public void setParcelCount(Integer parcelCount) {
        this.parcelCount = parcelCount;
    }

    public Integer getNewParcelCount() {
        return newParcelCount;
    }

    public void setNewParcelCount(Integer newParcelCount) {
        this.newParcelCount = newParcelCount;
    }

    public Integer getParcelChangeValue() {
        return parcelChangeValue;
    }

    public void setParcelChangeValue(Integer parcelChangeValue) {
        this.parcelChangeValue = parcelChangeValue;
    }

    public String getParcelChangeMode() {
        return parcelChangeMode;
    }

    public void setParcelChangeMode(String parcelChangeMode) {
        this.parcelChangeMode = parcelChangeMode;
    }

    public Integer getMaxRecirculationCount() {
        return maxRecirculationCount;
    }

    public void setMaxRecirculationCount(Integer maxRecirculationCount) {
        this.maxRecirculationCount = maxRecirculationCount;
    }

    public Integer getInitialRunSpeed() {
        return initialRunSpeed;
    }

    public void setInitialRunSpeed(Integer initialRunSpeed) {
        this.initialRunSpeed = initialRunSpeed;
    }

    public Integer getFlexibleResources() {
        return flexibleResources;
    }

    public void setFlexibleResources(Integer flexibleResources) {
        this.flexibleResources = flexibleResources;
    }

    public Integer getPhoneBoomChute() {
        return phoneBoomChute;
    }

    public void setPhoneBoomChute(Integer phoneBoomChute) {
        this.phoneBoomChute = phoneBoomChute;
    }

    public Integer getDropChuteFlexible() {
        return dropChuteFlexible;
    }

    public void setDropChuteFlexible(Integer dropChuteFlexible) {
        this.dropChuteFlexible = dropChuteFlexible;
    }

    public Integer getFlexibleUnloadResources() {
        return flexibleUnloadResources;
    }

    public void setFlexibleUnloadResources(Integer flexibleUnloadResources) {
        this.flexibleUnloadResources = flexibleUnloadResources;
    }

    public Integer getSorterFullPercent() {
        return sorterFullPercent;
    }

    public void setSorterFullPercent(Integer sorterFullPercent) {
        this.sorterFullPercent = sorterFullPercent;
    }

    public Integer getMinimumCageOccupancy() {
        return minimumCageOccupancy;
    }

    public void setMinimumCageOccupancy(Integer minimumCageOccupancy) {
        this.minimumCageOccupancy = minimumCageOccupancy;
    }

    public Integer getMaximumCageOccupancy() {
        return maximumCageOccupancy;
    }

    public void setMaximumCageOccupancy(Integer maximumCageOccupancy) {
        this.maximumCageOccupancy = maximumCageOccupancy;
    }

    public String getGuiMode() {
        return guiMode;
    }

    public void setGuiMode(String guiMode) {
        this.guiMode = guiMode;
    }

    public String getAllSortedAt() {
        return allSortedAt;
    }

    public void setAllSortedAt(String allSortedAt) {
        this.allSortedAt = allSortedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}