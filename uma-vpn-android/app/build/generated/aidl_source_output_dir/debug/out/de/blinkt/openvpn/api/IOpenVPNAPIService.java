/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package de.blinkt.openvpn.api;
public interface IOpenVPNAPIService extends android.os.IInterface
{
  /** Default implementation for IOpenVPNAPIService. */
  public static class Default implements de.blinkt.openvpn.api.IOpenVPNAPIService
  {
    @Override public java.util.List getProfiles() throws android.os.RemoteException
    {
      return null;
    }
    @Override public void startProfile(java.lang.String profileUUID) throws android.os.RemoteException
    {
    }
    @Override public boolean addVPNProfile(java.lang.String name, java.lang.String config) throws android.os.RemoteException
    {
      return false;
    }
    @Override public void startVPN(java.lang.String inlineconfig) throws android.os.RemoteException
    {
    }
    @Override public android.content.Intent prepare(java.lang.String packagename) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.content.Intent prepareVPNService() throws android.os.RemoteException
    {
      return null;
    }
    @Override public void disconnect() throws android.os.RemoteException
    {
    }
    @Override public void pause() throws android.os.RemoteException
    {
    }
    @Override public void resume() throws android.os.RemoteException
    {
    }
    @Override public void registerStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException
    {
    }
    @Override public void unregisterStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException
    {
    }
    @Override public void removeProfile(java.lang.String profileUUID) throws android.os.RemoteException
    {
    }
    @Override public boolean protectSocket(android.os.ParcelFileDescriptor fd) throws android.os.RemoteException
    {
      return false;
    }
    @Override public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfile(java.lang.String name, boolean userEditable, java.lang.String config) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void startVPNwithExtras(java.lang.String inlineconfig, android.os.Bundle extras) throws android.os.RemoteException
    {
    }
    @Override public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfileWithExtras(java.lang.String name, boolean userEditable, java.lang.String config, android.os.Bundle extras) throws android.os.RemoteException
    {
      return null;
    }
    @Override public de.blinkt.openvpn.api.APIVpnProfile getDefaultProfile() throws android.os.RemoteException
    {
      return null;
    }
    @Override public void setDefaultProfile(java.lang.String profileUUID) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements de.blinkt.openvpn.api.IOpenVPNAPIService
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an de.blinkt.openvpn.api.IOpenVPNAPIService interface,
     * generating a proxy if needed.
     */
    public static de.blinkt.openvpn.api.IOpenVPNAPIService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof de.blinkt.openvpn.api.IOpenVPNAPIService))) {
        return ((de.blinkt.openvpn.api.IOpenVPNAPIService)iin);
      }
      return new de.blinkt.openvpn.api.IOpenVPNAPIService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_getProfiles:
        {
          java.util.List _result = this.getProfiles();
          reply.writeNoException();
          reply.writeList(_result);
          break;
        }
        case TRANSACTION_startProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.startProfile(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_addVPNProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          boolean _result = this.addVPNProfile(_arg0, _arg1);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_startVPN:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.startVPN(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_prepare:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.content.Intent _result = this.prepare(_arg0);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_prepareVPNService:
        {
          android.content.Intent _result = this.prepareVPNService();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_disconnect:
        {
          this.disconnect();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_pause:
        {
          this.pause();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_resume:
        {
          this.resume();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_registerStatusCallback:
        {
          de.blinkt.openvpn.api.IOpenVPNStatusCallback _arg0;
          _arg0 = de.blinkt.openvpn.api.IOpenVPNStatusCallback.Stub.asInterface(data.readStrongBinder());
          this.registerStatusCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterStatusCallback:
        {
          de.blinkt.openvpn.api.IOpenVPNStatusCallback _arg0;
          _arg0 = de.blinkt.openvpn.api.IOpenVPNStatusCallback.Stub.asInterface(data.readStrongBinder());
          this.unregisterStatusCallback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_removeProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.removeProfile(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_protectSocket:
        {
          android.os.ParcelFileDescriptor _arg0;
          _arg0 = _Parcel.readTypedObject(data, android.os.ParcelFileDescriptor.CREATOR);
          boolean _result = this.protectSocket(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_addNewVPNProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _arg1;
          _arg1 = (0!=data.readInt());
          java.lang.String _arg2;
          _arg2 = data.readString();
          de.blinkt.openvpn.api.APIVpnProfile _result = this.addNewVPNProfile(_arg0, _arg1, _arg2);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_startVPNwithExtras:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.os.Bundle _arg1;
          _arg1 = _Parcel.readTypedObject(data, android.os.Bundle.CREATOR);
          this.startVPNwithExtras(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_addNewVPNProfileWithExtras:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _arg1;
          _arg1 = (0!=data.readInt());
          java.lang.String _arg2;
          _arg2 = data.readString();
          android.os.Bundle _arg3;
          _arg3 = _Parcel.readTypedObject(data, android.os.Bundle.CREATOR);
          de.blinkt.openvpn.api.APIVpnProfile _result = this.addNewVPNProfileWithExtras(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_getDefaultProfile:
        {
          de.blinkt.openvpn.api.APIVpnProfile _result = this.getDefaultProfile();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_setDefaultProfile:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.setDefaultProfile(_arg0);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements de.blinkt.openvpn.api.IOpenVPNAPIService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.util.List getProfiles() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getProfiles, _data, _reply, 0);
          _reply.readException();
          java.lang.ClassLoader cl = (java.lang.ClassLoader)this.getClass().getClassLoader();
          _result = _reply.readArrayList(cl);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void startProfile(java.lang.String profileUUID) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(profileUUID);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startProfile, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public boolean addVPNProfile(java.lang.String name, java.lang.String config) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _data.writeString(config);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addVPNProfile, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void startVPN(java.lang.String inlineconfig) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(inlineconfig);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startVPN, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public android.content.Intent prepare(java.lang.String packagename) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.content.Intent _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(packagename);
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepare, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.content.Intent.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.content.Intent prepareVPNService() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.content.Intent _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_prepareVPNService, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.content.Intent.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void disconnect() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_disconnect, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void pause() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_pause, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void resume() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_resume, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void registerStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(cb);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerStatusCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unregisterStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(cb);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterStatusCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void removeProfile(java.lang.String profileUUID) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(profileUUID);
          boolean _status = mRemote.transact(Stub.TRANSACTION_removeProfile, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public boolean protectSocket(android.os.ParcelFileDescriptor fd) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, fd, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_protectSocket, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfile(java.lang.String name, boolean userEditable, java.lang.String config) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        de.blinkt.openvpn.api.APIVpnProfile _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _data.writeInt(((userEditable)?(1):(0)));
          _data.writeString(config);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addNewVPNProfile, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, de.blinkt.openvpn.api.APIVpnProfile.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void startVPNwithExtras(java.lang.String inlineconfig, android.os.Bundle extras) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(inlineconfig);
          _Parcel.writeTypedObject(_data, extras, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startVPNwithExtras, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfileWithExtras(java.lang.String name, boolean userEditable, java.lang.String config, android.os.Bundle extras) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        de.blinkt.openvpn.api.APIVpnProfile _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _data.writeInt(((userEditable)?(1):(0)));
          _data.writeString(config);
          _Parcel.writeTypedObject(_data, extras, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_addNewVPNProfileWithExtras, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, de.blinkt.openvpn.api.APIVpnProfile.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public de.blinkt.openvpn.api.APIVpnProfile getDefaultProfile() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        de.blinkt.openvpn.api.APIVpnProfile _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getDefaultProfile, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, de.blinkt.openvpn.api.APIVpnProfile.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void setDefaultProfile(java.lang.String profileUUID) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(profileUUID);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setDefaultProfile, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_getProfiles = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_startProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_addVPNProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_startVPN = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_prepare = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_prepareVPNService = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_disconnect = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_pause = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_resume = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_registerStatusCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_unregisterStatusCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_removeProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_protectSocket = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_addNewVPNProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_startVPNwithExtras = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_addNewVPNProfileWithExtras = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_getDefaultProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_setDefaultProfile = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
  }
  public static final java.lang.String DESCRIPTOR = "de.blinkt.openvpn.api.IOpenVPNAPIService";
  public java.util.List getProfiles() throws android.os.RemoteException;
  public void startProfile(java.lang.String profileUUID) throws android.os.RemoteException;
  public boolean addVPNProfile(java.lang.String name, java.lang.String config) throws android.os.RemoteException;
  public void startVPN(java.lang.String inlineconfig) throws android.os.RemoteException;
  public android.content.Intent prepare(java.lang.String packagename) throws android.os.RemoteException;
  public android.content.Intent prepareVPNService() throws android.os.RemoteException;
  public void disconnect() throws android.os.RemoteException;
  public void pause() throws android.os.RemoteException;
  public void resume() throws android.os.RemoteException;
  public void registerStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException;
  public void unregisterStatusCallback(de.blinkt.openvpn.api.IOpenVPNStatusCallback cb) throws android.os.RemoteException;
  public void removeProfile(java.lang.String profileUUID) throws android.os.RemoteException;
  public boolean protectSocket(android.os.ParcelFileDescriptor fd) throws android.os.RemoteException;
  public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfile(java.lang.String name, boolean userEditable, java.lang.String config) throws android.os.RemoteException;
  public void startVPNwithExtras(java.lang.String inlineconfig, android.os.Bundle extras) throws android.os.RemoteException;
  public de.blinkt.openvpn.api.APIVpnProfile addNewVPNProfileWithExtras(java.lang.String name, boolean userEditable, java.lang.String config, android.os.Bundle extras) throws android.os.RemoteException;
  public de.blinkt.openvpn.api.APIVpnProfile getDefaultProfile() throws android.os.RemoteException;
  public void setDefaultProfile(java.lang.String profileUUID) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
