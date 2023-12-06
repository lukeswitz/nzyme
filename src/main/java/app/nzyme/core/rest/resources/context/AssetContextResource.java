package app.nzyme.core.rest.resources.context;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.dot11.Dot11MacAddressMetadata;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.rest.UserAuthenticatedResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.requests.CreateMacAddressContextRequest;
import app.nzyme.core.rest.requests.UpdateMacAddressContextRequest;
import app.nzyme.core.rest.responses.context.*;
import app.nzyme.core.rest.responses.misc.ErrorResponse;
import app.nzyme.plugin.distributed.messaging.ClusterMessage;
import app.nzyme.plugin.distributed.messaging.MessageType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Path("/api/context")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class AssetContextResource extends UserAuthenticatedResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("/mac/organization/show/{organization_id}/tenant/show/{tenant_id}")
    public Response macs(@Context SecurityContext sc,
                         @PathParam("organization_id") UUID organizationId,
                         @PathParam("tenant_id") UUID tenantId,
                         @QueryParam("address_filter") @Nullable String addressFilter,
                         @QueryParam("limit") @Max(250) int limit,
                         @QueryParam("offset") int offset) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (addressFilter == null || addressFilter.trim().isEmpty()) {
            // Set to SQL wildcard matcher to find all addresses if no filter set.
            addressFilter = "%";
        } else {
            addressFilter = "%" + addressFilter.trim().toUpperCase() + "%";
        }

        long count = nzyme.getContextService().countMacAddressContext(organizationId, tenantId);

        List<MacAddressContextDetailsResponse> addresses = Lists.newArrayList();
        for (MacAddressContextEntry m : nzyme.getContextService()
                .findAllMacAddressContext(organizationId, tenantId, addressFilter, limit, offset)) {
            addresses.add(entryToResponse(m));
        }

        return Response.ok(MacAddressContextListResponse.create(count, addresses)).build();
    }

    @GET
    @Path("/mac/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    public Response macByUuid(@Context SecurityContext sc,
                              @PathParam("organization_id") UUID organizationId,
                              @PathParam("tenant_id") UUID tenantId,
                              @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<MacAddressContextEntry> ctx = nzyme.getContextService()
                .findMacAddressContext(uuid, organizationId, tenantId);

        if (ctx.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(entryToResponse(ctx.get())).build();
    }

    @GET
    @Path("/mac/show/{mac}")
    public Response mac(@Context SecurityContext sc,
                        @PathParam("mac") @MacAddress String mac) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);

        Optional<MacAddressContextEntry> ctx = nzyme.getContextService().findMacAddressContext(
                mac, authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId()
        );

        MacAddressContextDetailsResponse contextDetails;
        if (ctx.isPresent()) {
            MacAddressContextEntry context = ctx.get();
            contextDetails = MacAddressContextDetailsResponse.create(
                    context.uuid(),
                    context.macAddress(),
                    context.name(),
                    context.description(),
                    context.notes(),
                    context.organizationId(),
                    nzyme.getAuthenticationService().findOrganization(context.organizationId()).get().name(),
                    context.tenantId(),
                    nzyme.getAuthenticationService().findTenant(context.tenantId()).get().name(),
                    context.createdAt(),
                    context.updatedAt()
            );
        } else {
            contextDetails = null;
        }

        Dot11MacAddressMetadata dot11Metadata = nzyme.getDot11().getMacAddressMetadata(
                mac,
                nzyme.getTapManager().allTapUUIDsAccessibleByUser(authenticatedUser)
        );

        // This is where will hook in for Ethernet types.
        MacAddressContextTypeResponse contextType;
        switch (dot11Metadata.type()) {
            case ACCESS_POINT:
                contextType = MacAddressContextTypeResponse.DOT11_AP;
                break;
            case CLIENT:
                contextType = MacAddressContextTypeResponse.DOT11_CLIENT;
                break;
            case MULTIPLE:
                contextType = MacAddressContextTypeResponse.DOT11_MIXED;
                break;
            case UNKNOWN:
            default:
                contextType = MacAddressContextTypeResponse.UNKNOWN;
        }

        // Find the first monitored SSID this MAC address may be part of. Unlikely to be more than one.
        Dot11MonitoredNetworkContextResponse servesMonitoredNetwork = null;
        for (MonitoredSSID monitoredNetwork : nzyme.getDot11()
                .findAllMonitoredSSIDs(authenticatedUser.getOrganizationId(), authenticatedUser.getTenantId())) {
            for (MonitoredBSSID bssid : nzyme.getDot11().findMonitoredBSSIDsOfSSID(monitoredNetwork.id())) {
                if (bssid.bssid().equalsIgnoreCase(mac)) {
                    servesMonitoredNetwork = Dot11MonitoredNetworkContextResponse.create(
                            monitoredNetwork.uuid(),
                            monitoredNetwork.isEnabled(),
                            monitoredNetwork.ssid()
                    );
                }
            }
        }

        return Response.ok(EnrichedMacAddressContextDetailsResponse.create(
                contextDetails, contextType, servesMonitoredNetwork
        )).build();
    }

    @POST
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_aliases_manage" })
    @Path("/mac")
    public Response createMac(@Context SecurityContext sc, CreateMacAddressContextRequest req) {
        if (!passedTenantDataAccessible(sc, req.organizationId(), req.tenantId())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this address exist already?
        if (nzyme.getContextService()
                .findMacAddressContext(req.macAddress(), req.organizationId(), req.tenantId()).isPresent()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.create("Context for this MAC address already exists."))
                    .build();
        }

        nzyme.getContextService().createMacAddressContext(
                req.macAddress(),
                req.name(),
                req.description(),
                req.notes(),
                req.organizationId(),
                req.tenantId()
        );

        // Invalidate caches.
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.INVALIDATE_CACHE,
                Map.of("cache_type", "context_macs"),
                false
        ));

        return Response.status(Response.Status.CREATED).build();
    }

    @PUT
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_aliases_manage" })
    @Path("/mac/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    public Response updateMac(@Context SecurityContext sc,
                              @Valid UpdateMacAddressContextRequest req,
                              @PathParam("organization_id") UUID organizationId,
                              @PathParam("tenant_id") UUID tenantId,
                              @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Does this context exist for org and tenant? Don't allow to change org or tenant on existing context.
        if (nzyme.getContextService().findMacAddressContext(uuid, organizationId, tenantId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().updateMacAddressContext(
                uuid, organizationId, tenantId, req.name(), req.description(), req.notes()
        );

        return Response.ok().build();
    }

    @DELETE
    @RESTSecured(value = PermissionLevel.ANY, featurePermissions = { "mac_aliases_manage" })
    @Path("/mac/organization/show/{organization_id}/tenant/show/{tenant_id}/uuid/{uuid}")
    public Response deleteMac(@Context SecurityContext sc,
                              @PathParam("organization_id") UUID organizationId,
                              @PathParam("tenant_id") UUID tenantId,
                              @PathParam("uuid") UUID uuid) {
        if (!passedTenantDataAccessible(sc, organizationId, tenantId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        nzyme.getContextService().deleteMacAddressContext(uuid, organizationId, tenantId);

        return Response.status(Response.Status.OK).build();
    }

    private MacAddressContextDetailsResponse entryToResponse(MacAddressContextEntry m) {
        return MacAddressContextDetailsResponse.create(
                m.uuid(),
                m.macAddress(),
                m.name(),
                m.description(),
                m.notes(),
                m.organizationId(),
                null,
                m.tenantId(),
                null,
                m.createdAt(),
                m.updatedAt()
        );
    }


}
