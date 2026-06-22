package com.icentric.Icentric.simulation.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icentric.Icentric.simulation.dto.CreateSimulationRequest;
import com.icentric.Icentric.simulation.dto.UpdateSimulationRequest;
import com.icentric.Icentric.simulation.entity.Simulation;
import com.icentric.Icentric.simulation.repository.SimulationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/simulations")
public class PlatformSimulationController {

    private final SimulationRepository repository;
    private final ObjectMapper objectMapper;

    public PlatformSimulationController(SimulationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<Simulation>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{simId}")
    public ResponseEntity<Simulation> getBySimId(@PathVariable String simId) {
        return ResponseEntity.ok(
                repository.findBySimId(simId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation not found: " + simId))
        );
    }

    @PostMapping
    public ResponseEntity<Simulation> create(@Valid @RequestBody CreateSimulationRequest request) {
        String simId = generateSimId(request.getTitle());
        if (repository.existsBySimId(simId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sim_id already exists: " + simId);
        }
        Simulation sim = new Simulation();
        sim.setSimId(simId);
        sim.setTitle(request.getTitle());
        sim.setData(toJsonString(request.getData()));
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(sim));
    }

    private String generateSimId(String title) {
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return "sim_" + slug + "_" + shortId;
    }

    @PutMapping("/{simId}")
    public ResponseEntity<Simulation> update(@PathVariable String simId, @Valid @RequestBody UpdateSimulationRequest request) {
        Simulation sim = repository.findBySimId(simId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation not found: " + simId));
        if (request.getTitle() != null) {
            sim.setTitle(request.getTitle());
        }
        sim.setData(toJsonString(request.getData()));
        return ResponseEntity.ok(repository.save(sim));
    }

    @DeleteMapping("/{simId}")
    public ResponseEntity<Void> delete(@PathVariable String simId) {
        Simulation sim = repository.findBySimId(simId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation not found: " + simId));
        repository.delete(sim);
        return ResponseEntity.noContent().build();
    }

    private String toJsonString(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON in data field");
        }
    }
}
