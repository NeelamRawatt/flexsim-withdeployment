package com.example.experiment_service.service;

import static com.example.experiment_service.testsupport.StatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.experiment_service.dto.TemplateItemDto;
import com.example.experiment_service.dto.TemplateListResponseDto;
import com.example.experiment_service.dto.TemplateSaveRequestDto;
import com.example.experiment_service.entity.ResourceTemplate;
import com.example.experiment_service.entity.ResourceTemplateRow;
import com.example.experiment_service.enums.TemplateType;
import com.example.experiment_service.repository.ResourceTemplateRepository;
import com.example.experiment_service.repository.ResourceTemplateRowRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ResourceTemplateServiceTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(7L, "neelam", "ADMIN");

    @Mock
    private ResourceTemplateRepository templateRepository;
    @Mock
    private ResourceTemplateRowRepository rowRepository;

    private ResourceTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ResourceTemplateService(templateRepository, rowRepository);
    }

    // ------------------------------------------------------------ test data

    private static Map<String, Object> infeedRow(String shiftId, String tc, Object resourceCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("shiftId", shiftId);
        row.put("tc", tc);
        row.put("resourceCount", resourceCount);
        return row;
    }

    private static TemplateSaveRequestDto infeedRequest(List<Map<String, Object>> rows) {
        return new TemplateSaveRequestDto("infeed", "My infeed", "my.xlsx", rows);
    }

    private void saveReturnsSameEntity() {
        when(templateRepository.save(any(ResourceTemplate.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ save: good

    @Test
    void savesTemplateWithOwnerFromTheTokenAndRowsInOrder() {
        saveReturnsSameEntity();
        List<Map<String, Object>> rows = List.of(infeedRow("1", "TCA", 3), infeedRow("2", "TCB", "4"));

        TemplateItemDto result = service.save(infeedRequest(rows), USER);

        ArgumentCaptor<ResourceTemplate> saved = ArgumentCaptor.forClass(ResourceTemplate.class);
        verify(templateRepository).save(saved.capture());
        ResourceTemplate entity = saved.getValue();
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getOwnerUsername()).isEqualTo("neelam");
        assertThat(entity.getTemplateType()).isEqualTo(TemplateType.INFEED);
        assertThat(entity.getRowCount()).isEqualTo(2);
        assertThat(entity.getRows()).hasSize(2);
        assertThat(entity.getRows().get(0).getRowIndex()).isEqualTo(0);
        assertThat(entity.getRows().get(1).getRowIndex()).isEqualTo(1);
        assertThat(entity.getRows().get(0).getTemplate()).isSameAs(entity);   // both sides of the link are set

        assertThat(result.type()).isEqualTo("infeed");
        assertThat(result.name()).isEqualTo("My infeed");
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.data()).hasSize(2);
    }

    @Test
    void usesDefaultNamesWhenMissing() {
        saveReturnsSameEntity();

        TemplateItemDto result = service.save(
                new TemplateSaveRequestDto("infeed", "  ", null, List.of(infeedRow("1", "TCA", 1))), USER);

        assertThat(result.name()).isEqualTo("Infeed Template");
        assertThat(result.fileName()).isEqualTo("template-data");
    }

    @Test
    void acceptsSortPlanSpellingsAndWholeNumbersWithDecimalZero() {
        saveReturnsSameEntity();
        Map<String, Object> plan = Map.of("oldSortName", "A", "newSortName", "B");

        assertThat(service.save(new TemplateSaveRequestDto("sortPlan", null, null, List.of(plan)), USER).type())
                .isEqualTo("sort_plan");
        assertThat(service.save(new TemplateSaveRequestDto("SORT-PLAN", null, null, List.of(plan)), USER).type())
                .isEqualTo("sort_plan");
        assertThat(service.save(infeedRequest(List.of(infeedRow("1", "TCA", "3.0"))), USER).rowCount()).isEqualTo(1);
    }

    @Test
    void savesZoneTemplate() {
        saveReturnsSameEntity();
        Map<String, Object> zone = Map.of("shiftId", "1", "zoneId", "Z1", "chuteIdRaw", "1,2,3");

        TemplateItemDto result = service.save(new TemplateSaveRequestDto("zone", null, null, List.of(zone)), USER);

        assertThat(result.type()).isEqualTo("zone");
        assertThat(result.name()).isEqualTo("Zone Template");
    }

    // ------------------------------------------------------------ save: bad

    @Test
    void badTypeGives400() {
        List<Map<String, Object>> rows = List.of(infeedRow("1", "TCA", 1));
        assertStatus(() -> service.save(new TemplateSaveRequestDto(null, null, null, rows), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(new TemplateSaveRequestDto("pizza", null, null, rows), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void missingBodyOrEmptyDataGives400() {
        assertStatus(() -> service.save(null, USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of()), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(null), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void infeedResourceCountMustBeAWholeNumberNotNegative() {
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", "TCA", -2))), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", "TCA", "abc"))), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", "TCA", "2.5"))), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", "TCA", null))), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void aMissingValueIsNotAcceptedAsTheTextNull() {
        // The old app turned a missing value into the text "null" and accepted it. We do not.
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow(null, "TCA", 1))), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", null, 1))), USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(infeedRequest(List.of(infeedRow("1", "   ", 1))), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void zoneAndSortPlanRowsNeedTheirRequiredColumns() {
        Map<String, Object> zoneWithoutChutes = Map.of("shiftId", "1", "zoneId", "Z1");
        Map<String, Object> planWithoutNewName = Map.of("oldSortName", "A");

        assertStatus(() -> service.save(new TemplateSaveRequestDto("zone", null, null, List.of(zoneWithoutChutes)), USER),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(new TemplateSaveRequestDto("sort_plan", null, null, List.of(planWithoutNewName)), USER),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void sizeLimitsGive400() {
        Map<String, Object> valid = infeedRow("1", "TCA", 1);

        // too many rows
        assertStatus(() -> service.save(infeedRequest(Collections.nCopies(5001, valid)), USER), HttpStatus.BAD_REQUEST);

        // too many columns in one row
        Map<String, Object> wide = new HashMap<>(valid);
        for (int i = 0; i < 50; i++) {
            wide.put("extra" + i, i);
        }
        assertStatus(() -> service.save(infeedRequest(List.of(wide)), USER), HttpStatus.BAD_REQUEST);

        // one text value that is too long
        Map<String, Object> longCell = new HashMap<>(valid);
        longCell.put("note", "x".repeat(1001));
        assertStatus(() -> service.save(infeedRequest(List.of(longCell)), USER), HttpStatus.BAD_REQUEST);

        // name that is too long
        assertStatus(() -> service.save(
                new TemplateSaveRequestDto("infeed", "n".repeat(256), null, List.of(valid)), USER), HttpStatus.BAD_REQUEST);

        verifyNoInteractions(templateRepository);
    }

    // ---------------------------------------------------------------- delete

    @Test
    void deleteOfATemplateThatIsNotFoundOrNotYoursGives404() {
        when(templateRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());

        assertStatus(() -> service.delete(5L, USER), HttpStatus.NOT_FOUND);

        verify(templateRepository, never()).deleteTemplateById(any());
    }

    @Test
    void deleteOfYourOwnTemplateRunsOneDeleteStatement() {
        when(templateRepository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(new ResourceTemplate()));

        service.delete(5L, USER);

        verify(templateRepository).deleteTemplateById(5L);
    }

    // ------------------------------------------------------------------ list

    private static ResourceTemplate template(Long id, TemplateType type) {
        ResourceTemplate t = new ResourceTemplate();
        t.setId(id);
        t.setTemplateType(type);
        t.setTemplateName("t" + id);
        t.setFileName("f.xlsx");
        t.setRowCount(1);
        return t;
    }

    @Test
    void listGroupsTemplatesByTypeAndFillsRowsWithOneRowQuery() {
        ResourceTemplate infeed = template(1L, TemplateType.INFEED);
        ResourceTemplate zone = template(2L, TemplateType.ZONE);
        ResourceTemplateRow row = new ResourceTemplateRow();
        row.setTemplate(infeed);
        row.setRowIndex(0);
        row.setRowData(Map.of("tc", "TCA"));

        when(templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.INFEED)).thenReturn(List.of(infeed));
        when(templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.ZONE)).thenReturn(List.of(zone));
        when(templateRepository.findByTemplateTypeOrderByCreatedAtDesc(TemplateType.SORT_PLAN)).thenReturn(new ArrayList<>());
        when(rowRepository.findByTemplate_IdInOrderByTemplate_IdAscRowIndexAsc(anyCollection())).thenReturn(List.of(row));

        TemplateListResponseDto result = service.list();

        assertThat(result.infeed()).hasSize(1);
        assertThat(result.infeed().get(0).data()).hasSize(1);
        assertThat(result.infeed().get(0).data().get(0)).containsEntry("tc", "TCA");
        assertThat(result.zone()).hasSize(1);
        assertThat(result.zone().get(0).data()).isEmpty();
        assertThat(result.sortPlan()).isEmpty();
        verify(rowRepository).findByTemplate_IdInOrderByTemplate_IdAscRowIndexAsc(anyCollection());
    }

    @Test
    void listWithNoTemplatesDoesNotAskForRowsAtAll() {
        TemplateListResponseDto result = service.list();

        assertThat(result.infeed()).isEmpty();
        assertThat(result.zone()).isEmpty();
        assertThat(result.sortPlan()).isEmpty();
        verifyNoInteractions(rowRepository);
    }
}