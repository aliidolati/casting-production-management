package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.PieceStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PieceRepository extends JpaRepository<Piece, UUID>, PieceRepositoryCustom {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Piece p where p.id = :id")
    Optional<Piece> lockById(@Param("id") UUID id);

    @Query("""
        select p from Piece p join fetch p.pieceDefinition d join fetch d.project project
        where p.archivedAt is null
          and (:approvedPassportId is not null or p.currentStage = com.factory.productmanager.model.entity.PieceStage.CASTING)
          and not exists (select 1 from MeltPassportPiece link where link.activePieceKey = p.id)
          and not exists (select 1 from MeltPassportPiece link where link.passport.id = :approvedPassportId and link.piece.id = p.id)
          and (:empty = true
            or (:numeric = true and p.pieceNumber = :number)
            or (:numeric = false and (lower(d.pieceName) like :pattern escape '\\'
                or lower(project.code) like :pattern escape '\\'
                or (lower(project.code) = :referenceProject and d.rowNum = :referenceRow
                    and p.pieceNumber = :referenceNumber))))
        order by project.code, d.rowNum, p.pieceNumber
        """)
    List<Piece> lookupMeltCandidates(@Param("empty") boolean empty, @Param("numeric") boolean numeric,
            @Param("number") Integer number, @Param("pattern") String pattern,
            @Param("referenceProject") String referenceProject, @Param("referenceRow") Integer referenceRow,
            @Param("referenceNumber") Integer referenceNumber, @Param("approvedPassportId") UUID approvedPassportId,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        select p from Piece p join fetch p.pieceDefinition d join fetch d.project project
        where p.currentStage = com.factory.productmanager.model.entity.PieceStage.SCRAPPED
          and (:projectId is null or project.id = :projectId)
        """)
    List<Piece> findScrappedIncludingArchived(@Param("projectId") UUID projectId);

    List<Piece> findByPieceNumberAndArchivedAtIsNull(Integer pieceNumber);
    boolean existsByPieceDefinitionIdAndPieceNumber(UUID pieceDefinitionId, Integer pieceNumber);

    boolean existsByPieceDefinitionIdAndPieceNumberAndIdNot(UUID pieceDefinitionId, Integer pieceNumber, UUID id);

    List<Piece> findByPieceDefinitionIdAndArchivedAtIsNull(UUID pieceDefinitionId);

    List<Piece> findByPieceDefinitionIdAndArchivedAtIsNotNull(UUID pieceDefinitionId);

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE d.id = :definitionId
        ORDER BY p.pieceNumber
        """)
    List<Piece> findAllWithDefinitionAndProjectByDefinitionId(@Param("definitionId") UUID definitionId);

    List<Piece> findByArchivedAtIsNull();

    long countByArchivedAtIsNull();

    long countByArchivedAtIsNullAndPieceDefinition_Project_Id(UUID projectId);

    @Query("SELECT p.pieceNumber FROM Piece p WHERE p.pieceDefinition.id = :pieceDefinitionId")
    List<Integer> findPieceNumbersByPieceDefinitionId(@Param("pieceDefinitionId") UUID pieceDefinitionId);

    List<Piece> findByArchivedAtIsNullAndCurrentStageNotIn(Collection<PieceStage> stages);

    List<Piece> findByArchivedAtIsNullAndCurrentStageNotInAndPieceDefinition_Project_Id(
            Collection<PieceStage> stages,
            UUID projectId
    );

    List<Piece> findByArchivedAtIsNullAndPieceDefinition_Project_Id(UUID projectId);

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE p.currentStage = :stage
          AND p.archivedAt IS NULL
        """)
    List<Piece> findAllWithDefinitionAndProjectByCurrentStage(@Param("stage") PieceStage stage);

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE p.currentStage = :stage
          AND proj.id = :projectId
          AND p.archivedAt IS NULL
        """)
    List<Piece> findAllWithDefinitionAndProjectByCurrentStageAndProject(
            @Param("stage") PieceStage stage,
            @Param("projectId") UUID projectId
    );

    @Query("""
        SELECT p FROM Piece p
        WHERE p.archivedAt IS NULL
          AND (
            LOWER(p.pieceDefinition.project.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.pieceDefinition.pieceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR CAST(p.pieceNumber AS string) LIKE CONCAT('%', :keyword, '%')
          )
        """)
    List<Piece> searchPieces(@Param("keyword") String keyword);

    @Query("SELECT p.currentStage, COUNT(p) FROM Piece p WHERE p.archivedAt IS NULL GROUP BY p.currentStage")
    List<Object[]> countGroupByCurrentStage();

    @Query("""
        SELECT p.currentStage, COUNT(p)
        FROM Piece p
        WHERE p.pieceDefinition.project.id = :projectId
          AND p.archivedAt IS NULL
        GROUP BY p.currentStage
        """)
    List<Object[]> countGroupByCurrentStageForProject(@Param("projectId") UUID projectId);

    /** Current stage totals including archived pieces, for the lifetime scrap ratio. */
    @Query("""
        SELECT p.currentStage, COUNT(p)
        FROM Piece p
        WHERE (:projectId IS NULL OR p.pieceDefinition.project.id = :projectId)
        GROUP BY p.currentStage
        """)
    List<Object[]> countGroupByCurrentStageIncludingArchived(@Param("projectId") UUID projectId);

    long countByPieceDefinition_Project_Id(UUID projectId);

    @Query("""
        SELECT p.pieceDefinition.id, COUNT(p)
        FROM Piece p
        WHERE p.pieceDefinition.project.id = :projectId
          AND p.archivedAt IS NULL
        GROUP BY p.pieceDefinition.id
        """)
    List<Object[]> countPiecesGroupedByDefinitionForProject(@Param("projectId") UUID projectId);

    @Query("""
        SELECT p.id
        FROM Piece p
        WHERE p.pieceDefinition.project.id = :projectId
          AND p.currentStage <> :excludedStage
          AND p.archivedAt IS NULL
          AND (:pieceDefinitionId IS NULL OR p.pieceDefinition.id = :pieceDefinitionId)
        """)
    List<UUID> findIdsByProjectExcludingStage(
            @Param("projectId") UUID projectId,
            @Param("excludedStage") PieceStage excludedStage,
            @Param("pieceDefinitionId") UUID pieceDefinitionId
    );

    @Query("""
        SELECT p.pieceDefinition.id, COUNT(p)
        FROM Piece p
        WHERE p.id IN :pieceIds
          AND p.archivedAt IS NULL
        GROUP BY p.pieceDefinition.id
        """)
    List<Object[]> countPiecesGroupedByDefinitionForIds(@Param("pieceIds") Collection<UUID> pieceIds);

    @Query("""
        SELECT p.id
        FROM Piece p
        WHERE p.pieceDefinition.project.id = :projectId
          AND p.pieceDefinition.id IN :definitionIds
          AND p.archivedAt IS NULL
        """)
    List<UUID> findIdsByProjectAndDefinitionIds(
            @Param("projectId") UUID projectId,
            @Param("definitionIds") Collection<UUID> definitionIds
    );

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE p.archivedAt IS NULL
        """)
    List<Piece> findAllWithDefinitionAndProject();

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE proj.id = :projectId
          AND p.archivedAt IS NULL
        """)
    List<Piece> findAllWithDefinitionAndProjectByProjectId(@Param("projectId") UUID projectId);

    @Query("""
        SELECT DISTINCT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE p.id IN :ids
        """)
    List<Piece> findWithDefinitionAndProjectByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE proj.code = :projectCode
          AND d.rowNum = :rowNum
          AND p.pieceNumber = :pieceNumber
        """)
    Optional<Piece> findByProjectCodeAndRowNumAndPieceNumber(
            @Param("projectCode") String projectCode,
            @Param("rowNum") Integer rowNum,
            @Param("pieceNumber") Integer pieceNumber
    );

    /** وظایف: جستجوی سبک و محدود قطعات برای انتخاب «قطعه‌ی مرتبط» (فقط قطعات بایگانی‌نشده) */
    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE p.archivedAt IS NULL
          AND (
            LOWER(proj.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.pieceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR CAST(p.pieceNumber AS string) LIKE CONCAT('%', :keyword, '%')
          )
        ORDER BY proj.code, d.rowNum, p.pieceNumber
        """)
    List<Piece> lookupForTasks(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT p FROM Piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE p.archivedAt IS NULL
          AND proj.id = :projectId
          AND (
            LOWER(d.pieceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR CAST(p.pieceNumber AS string) LIKE CONCAT('%', :keyword, '%')
          )
        ORDER BY d.rowNum, p.pieceNumber
        """)
    List<Piece> lookupForTasksInProject(
            @Param("projectId") UUID projectId,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable
    );
}
