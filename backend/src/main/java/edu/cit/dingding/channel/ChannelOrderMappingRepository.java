package edu.cit.dingding.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ChannelOrderMappingRepository extends JpaRepository<ChannelOrderMapping, Long> {
    Optional<ChannelOrderMapping> findByTiangeOrderId(String tiangeOrderId);
    Optional<ChannelOrderMapping> findByOurOrderId(Long ourOrderId);
}
