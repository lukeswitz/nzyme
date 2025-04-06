package app.nzyme.core.rest.resources.bluetooth;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.bluetooth.db.BluetoothDeviceSummary;
import app.nzyme.core.bluetooth.sig.BluetoothDeviceClass;
import app.nzyme.core.context.db.MacAddressContextEntry;
import app.nzyme.core.rest.RestTools;
import app.nzyme.core.rest.TapDataHandlingResource;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import app.nzyme.core.rest.constraints.MacAddress;
import app.nzyme.core.rest.responses.bluetooth.*;
import app.nzyme.core.rest.responses.shared.TapBasedSignalStrengthResponse;
import app.nzyme.core.shared.db.GenericIntegerHistogramEntry;
import app.nzyme.core.shared.db.TapBasedSignalStrengthResult;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRange;
import app.nzyme.core.util.Tools;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/bluetooth/devices")
@Produces(MediaType.APPLICATION_JSON)
@RESTSecured(PermissionLevel.ANY)
public class BluetoothDevicesResource extends TapDataHandlingResource {

    @Inject
    private NzymeNode nzyme;

    @GET
    public Response findAll(@Context SecurityContext sc,
                            @QueryParam("time_range") @Valid String timeRangeParameter,
                            @QueryParam("limit") int limit,
                            @QueryParam("offset") int offset,
                            @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        long total = nzyme.getBluetooth().countAllDevices(timeRange, tapUuids);

        List<BluetoothDeviceSummaryDetailsResponse> devices = Lists.newArrayList();
        for (BluetoothDeviceSummary dev : nzyme.getBluetooth().findAllDevices(timeRange, limit, offset, tapUuids)) {
            devices.add(buildResponse(dev, authenticatedUser));
        }

        return Response.ok(BluetoothDeviceSummaryListResponse.create(total, devices)).build();
    }

    @GET
    @Path("/show/{mac}")
    public Response findOne(@Context SecurityContext sc,
                            @PathParam("mac") @MacAddress String mac,
                            @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);

        Optional<BluetoothDeviceSummary> device = nzyme.getBluetooth().findOneDevice(mac, tapUuids);

        if (device.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(BluetoothDeviceDetailsResponse.create(
                buildResponse(device.get(), authenticatedUser)
        )).build();
    }

    @GET
    @Path("/show/{mac}/rssi/histogram")
    public Response rssiHistogram(@Context SecurityContext sc,
                                  @PathParam("mac") @MacAddress String mac,
                                  @QueryParam("time_range") @Valid String timeRangeParameter,
                                  @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);
        Bucketing.BucketingConfiguration bucketing = Bucketing.getConfig(timeRange);

        List<GenericIntegerHistogramEntry> histo = nzyme.getBluetooth()
                .getDeviceSignalStrengthHistogram(mac, timeRange, bucketing, tapUuids);

        return Response.ok(RestTools.genericHistogramToResponse(histo)).build();
    }

    @GET
    @Path("/show/{mac}/rssi/bytap")
    public Response rssiByTap(@Context SecurityContext sc,
                              @PathParam("mac") @MacAddress String mac,
                              @QueryParam("time_range") @Valid String timeRangeParameter,
                              @QueryParam("taps") String taps) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(sc);
        List<UUID> tapUuids = parseAndValidateTapIds(authenticatedUser, nzyme, taps);
        TimeRange timeRange = parseTimeRangeQueryParameter(timeRangeParameter);

        List<TapBasedSignalStrengthResponse> response = Lists.newArrayList();
        for (TapBasedSignalStrengthResult ss : nzyme.getBluetooth()
                .getDeviceSignalStrengthPerTap(mac, timeRange, tapUuids)) {
            response.add(TapBasedSignalStrengthResponse.create(
                    ss.tapUuid(),
                    ss.tapName(),
                    ss.signalStrength()
            ));
        }

        return Response.ok(response).build();
    }

    private BluetoothDeviceSummaryDetailsResponse buildResponse(BluetoothDeviceSummary dev,
                                                                AuthenticatedUser authenticatedUser) {
        Optional<MacAddressContextEntry> deviceContext = nzyme.getContextService().findMacAddressContext(
                dev.mac(),
                authenticatedUser.getOrganizationId(),
                authenticatedUser.getTenantId()
        );

        List<String> deviceClasses = buildDeviceClasses(dev);

        List<String> companies = Lists.newArrayList();
        for (Integer companyId : dev.companyIds()) {
            nzyme.getBluetoothSigService().lookupCompanyId(companyId)
                    .ifPresent(companies::add);
        }

        return BluetoothDeviceSummaryDetailsResponse.create(
                BluetoothMacAddressResponse.create(
                        dev.mac(),
                        nzyme.getOuiService().lookup(dev.mac()).orElse(null),
                        Tools.macAddressIsRandomized(dev.mac()),
                        deviceContext.map(macAddressContextEntry ->
                                        BluetoothMacAddressContextResponse.create(
                                                macAddressContextEntry.name(),
                                                macAddressContextEntry.description()
                                        ))
                                .orElse(null)
                ),
                dev.aliases(),
                dev.devices(),
                dev.transports(),
                dev.names(),
                dev.averageRssi(),
                companies,
                deviceClasses,
                dev.discoveredServices(),
                dev.tags(),
                dev.firstSeen(),
                dev.lastSeen()
        );
    }

    private static List<String> buildDeviceClasses(BluetoothDeviceSummary dev) {
        List<String> deviceClasses = Lists.newArrayList();
        for (Integer classNumber : dev.classNumbers()) {
            if (classNumber > 0) {
                BluetoothDeviceClass c = new BluetoothDeviceClass(classNumber);

                String minor = c.getMinorDeviceClass();
                String major = c.getMajorDeviceClass();

                if (minor != null) {
                    deviceClasses.add(minor);
                } else {
                    if (major != null) {
                        deviceClasses.add(major);
                    }
                }
            }
        }
        return deviceClasses;
    }

}
