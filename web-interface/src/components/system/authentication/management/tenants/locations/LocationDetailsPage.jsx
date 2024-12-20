import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import {Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import moment from "moment";
import FloorsTable from "./floors/FloorsTable";
import {notify} from "react-notify-toast";
import numeral from "numeral";

const authenticationManagementService = new AuthenticationManagementService();

function LocationDetailsPage() {

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { locationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [location, setLocation] = useState(null);

  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTenantLocation(locationId, organizationId, tenantId, setLocation)
  }, [organizationId, tenantId, locationId])

  const deleteLocation = () => {
    if (!confirm("Really delete location?")) {
      return;
    }

    authenticationManagementService.deleteTenantLocation(locationId, organizationId, tenantId, () => {
      notify.show('Location deleted.', 'success');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)} />
  }

  if (!organization || !tenant || !location) {
    return <LoadingSpinner />
  }

  return (
      <div className="row">
        <div className="col-md-9">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">
                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</a>
              </li>
              <li className="breadcrumb-item">Organizations</li>
              <li className="breadcrumb-item">
                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                  {organization.name}
                </a>
              </li>
              <li className="breadcrumb-item">
                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                  {tenant.name}
                </a>
              </li>
              <li className="breadcrumb-item">
                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                {tenant.name}
                </a>
              </li>
              <li className="breadcrumb-item">
                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
                  Locations
                </a>
              </li>
              <li className="breadcrumb-item active" aria-current="page">{location.name}</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
          <span className="float-end">
            <a className="btn btn-secondary"
               href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
              Back
            </a>{' '}
            <a className="btn btn-primary"
               href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.EDIT(organization.id, tenant.id, location.id)}>
                Edit Location
            </a>
          </span>
        </div>

        <div className="col-md-12">
          <h1>Location &quot;{location.name}&quot;</h1>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Description</h3>

                    <p>
                      {location.description ? location.description : <em>No Description.</em>}
                    </p>

                    <dl className="mb-0">
                      <dt>Taps placed at this location</dt>
                      <dd>{numeral(location.tap_count).format("0,0")}</dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Floors</h3>

                    <FloorsTable organizationId={organizationId} tenantId={tenantId} locationId={locationId} />

                    <a className="btn btn-sm btn-secondary" href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.FLOORS.CREATE(organization.id, tenant.id, location.id)}>
                      Create Floor
                    </a>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Delete Location</h3>

                    <p>
                      You can only delete a location if it has no floors.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={deleteLocation} disabled={location.floor_count !== 0}>
                      Delete Location
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Metadata</h3>

                    <dl className="mb-0">
                      <dt>Created At</dt>
                      <dd title={moment(location.created_at).format()}>{moment(location.created_at).fromNow()}</dd>
                      <dt>Updated At</dt>
                      <dd title={moment(location.updated_at).format()}>{moment(location.updated_at).fromNow()}</dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default LocationDetailsPage;