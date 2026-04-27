package com.sroadtutor.marketplace.repository;

import com.sroadtutor.marketplace.model.Dealership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DealershipRepository extends JpaRepository<Dealership, UUID> {

    List<Dealership> findByActiveTrue();

    List<Dealership> findByActiveTrueAndProvince(String province);
}
