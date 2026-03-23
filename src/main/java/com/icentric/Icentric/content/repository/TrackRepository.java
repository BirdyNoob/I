package com.icentric.Icentric.content.repository;

import com.icentric.Icentric.content.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackRepository
        extends JpaRepository<Track, UUID> {

    Optional<Track> findBySlug(String slug);
    List<Track> findByStatus(String status);

}
