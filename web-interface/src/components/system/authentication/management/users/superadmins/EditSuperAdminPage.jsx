import React, {useEffect, useState} from "react";
import {Navigate, useParams} from "react-router-dom";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import {notify} from "react-notify-toast";
import EditPasswordForm from "../shared/EditPasswordForm";
import EditUserForm from "../shared/EditUserForm";

const authenticationManagementService = new AuthenticationManagementService();

function EditSuperAdminPage() {

  const { userId } = useParams();

  const [errorMessage, setErrorMessage] = useState(null);
  const [user, setUser] = useState(null);

  const [redirect, setRedirect] = useState(false);

  const onEditDetailsFormSubmitted = function (email, name, disableMfa, callback) {
    authenticationManagementService.editSuperAdministrator(user.id, name, email, disableMfa, function() {
      // Success.
      notify.show('Super Administrator updated.', 'success');
      setRedirect(true);
      callback();
    }, function (error) {
      console.log(error);
      // Error.
      setErrorMessage(error.response.data.message);
      callback();
    })
  }

  const onEditPasswordFormSubmitted = function(password, callback) {
    authenticationManagementService.editSuperAdministratorPassword(user.id, password, function() {
      // Success.
      notify.show('Password updated.', 'success');
      setRedirect(true);
      callback();
    })
  }

  useEffect(() => {
    authenticationManagementService.findSuperAdmin(userId, setUser)
  }, [userId])

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SUPERADMINS.DETAILS(userId)} />
  }

  if (!user) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-9">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</a>
                </li>
                <li className="breadcrumb-item">Super Administrators</li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SUPERADMINS.DETAILS(userId)}>{user.email}</a>
                </li>
                <li className="breadcrumb-item active" aria-current="page">
                  Edit
                </li>
              </ol>
            </nav>
          </div>

          <div className="col-3">
              <span className="float-end">
                <a className="btn btn-secondary"
                   href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SUPERADMINS.DETAILS(userId)}>
                  Back
                </a>
              </span>
          </div>
        </div>

        <div className="row">
          <div className="col-12">
            <h1>Super Administrator &quot;{user.email}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Edit Super Administrator Details</h3>

                    <EditUserForm
                        email={user.email}
                        name={user.name}
                        disableMfa={user.mfa_disabled}
                        submitText="Edit Super Administrator"
                        errorMessage={errorMessage}
                        onClick={onEditDetailsFormSubmitted} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Change Password</h3>

                    <p>
                      Changing the password will log out the user and prompt them to log in again with the
                      new password.
                    </p>

                    <EditPasswordForm
                        submitText="Change Password"
                        onClick={onEditPasswordFormSubmitted} />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>


      </React.Fragment>
  )

}

export default EditSuperAdminPage;