package edu.cit.dingding.channel;

/**
 * The only public door into this module, per the lab's rule — nothing
 * outside channel currently needs to call this (Order/Inventory must not
 * know Tiangge exists, so the dependency direction is channel -> them,
 * never the reverse), but it keeps the same ACL shape as the supplier
 * module and gives the republish/retry paths a clean seam.
 */
public interface SalesChannelGateway {
    void publishListings();
    void publishStock(String productId, int available);
}
