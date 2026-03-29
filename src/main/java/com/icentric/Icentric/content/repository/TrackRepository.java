package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackRepository
        extends JpaRepository<Track, UUID> {

    Optional<Track> findBySlug(String slug);
    List<Track> findByStatus(String status);
    List<Track> findBySlugOrderByVersionDesc(String slug);
    Optional<Track> findTopBySlugOrderByVersionDesc(String slug);

    @Query("""
        SELECT t FROM Track t
        WHERE t.isPublished = true
          AND t.version = (
            SELECT MAX(t2.version) FROM Track t2
            WHERE t2.slug = t.slug
              AND t2.isPublished = true
          )
        ORDER BY t.title ASC
    """)
    List<Track> findLatestPublishedTracks();

}
