/*
 * KeyStoreUtil.java
 * This file is part of Portecle, a multipurpose keystore and certificate tool.
 *
 * Copyright © 2004 Wayne Grant, waynedgrant@hotmail.com
 *             2004-2005 Ville Skyttä, ville.skytta@iki.fi
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package net.sf.portecle.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Provides utility methods for loading/saving keystores.  The
 * Bouncy Castle provider must be registered before using this class to
 * create or load BKS or UBER type keystores, or PKCS #12 on Java 1.4.
 */
public final class KeyStoreUtil
{
    /** Resource bundle */
    private static ResourceBundle m_res =
        ResourceBundle.getBundle("net/sf/portecle/crypto/resources");

    /**
     * Private to prevent construction.
     */
    private KeyStoreUtil() {}


    /**
     * Gets the preferred (by us) KeyStore instance for the given
     * keystore type.
     *
     * @param keyStoreType The keystore type
     * @return The keystore
     * @throws KeyStoreException No implementation found
     */
    private static KeyStore getKeyStoreImpl(KeyStoreType keyStoreType)
        throws KeyStoreException
    {
        KeyStore keyStore = null;
        if (keyStoreType == KeyStoreType.PKCS12)
        {
            // Prefer BC for PKCS #12 for now; the BC and Sun 1.5
            // implementations (as of 1.29 and 1.5.0_03) are incompatible
            // in how they handle empty/missing passwords (null vs char[0]).
            try
            {
                keyStore = KeyStore.getInstance(keyStoreType.toString(), "BC");
            }
            catch (NoSuchProviderException ex)
            {
                // Fall through
            }
        }
        if (keyStore == null)
        {
            keyStore = KeyStore.getInstance(keyStoreType.toString());
        }
        return keyStore;
    }


    /**
     * Create a new, empty keystore.
     *
     * @param keyStoreType The keystore type to create
     * @return The keystore
     * @throws CryptoException Problem encountered creating the keystore
     * @throws IOException An I/O error occurred
     */
    public static KeyStore createKeyStore(KeyStoreType keyStoreType)
        throws CryptoException, IOException
    {
        KeyStore keyStore = null;
        try
        {
            keyStore = getKeyStoreImpl(keyStoreType);
            keyStore.load(null, null);
        }
        catch (GeneralSecurityException ex)
        {
            throw new CryptoException(
                m_res.getString("NoCreateKeystore.exception.message"), ex);
        }
        return keyStore;
    }


    /**
     * Load a Keystore from a file accessed by a password.
     *
     * @param keyStoreType The type of the keystore to open
     * @param fKeyStore File to load keystore from
     * @param cPassword Password of the keystore
     * @return The keystore
     * @throws CryptoException Problem encountered loading the keystore
     * @throws FileNotFoundException If the keystore file does not
     * exist, is a directory rather than a regular file, or for some
     * other reason cannot be opened for reading
     */
    public static KeyStore loadKeyStore(File fKeyStore, char[] cPassword,
                                        KeyStoreType keyStoreType)
        throws CryptoException, FileNotFoundException
    {
        KeyStore keyStore = null;
        try
        {
            keyStore = getKeyStoreImpl(keyStoreType);
        }
        catch (KeyStoreException ex)
        {
            throw new CryptoException(
                m_res.getString("NoCreateKeystore.exception.message"), ex);
        }

        FileInputStream fis = new FileInputStream(fKeyStore);
        try
        {
            keyStore.load(fis, cPassword);
        }
        catch (GeneralSecurityException ex)
        {
            throw new CryptoException(
                MessageFormat.format(
                    m_res.getString("NoLoadKeystore.exception.message"),
                    new Object[]{keyStoreType}), ex);
        }
        catch (FileNotFoundException ex)
        {
            throw ex;
        }
        catch (IOException ex)
        {
            throw new CryptoException(
                MessageFormat.format(
                    m_res.getString("NoLoadKeystore.exception.message"),
                    new Object[]{keyStoreType}), ex);
        }
        finally
        {
            try { fis.close(); } catch (IOException ex) { /* Ignore */ }
        }

        return keyStore;
    }


    /**
     * Load a PKCS #11 keystore accessed by a password.
     *
     * @param sPkcs11Provider The name of the PKCS #11 provider
     * @param cPassword Password of the keystore
     * @return The keystore
     * @throws CryptoException Problem encountered loading the keystore
     */
    public static KeyStore loadKeyStore(String sPkcs11Provider,
                                        char[] cPassword)
        throws CryptoException
    {
        KeyStore keyStore = null;

        try
        {
            if (Security.getProvider(sPkcs11Provider) == null)
            {
                throw new CryptoException(
                    MessageFormat.format(
                        m_res.getString("NoSuchProvider.exception.message"),
                        new String[]{sPkcs11Provider}));
            }
            keyStore = KeyStore.getInstance(
                KeyStoreType.PKCS11.toString(), sPkcs11Provider);
        }
        catch (GeneralSecurityException ex)
        {
            throw new CryptoException(
                m_res.getString("NoCreateKeystore.exception.message"), ex);
        }

        try
        {
            keyStore.load(null, cPassword);
        }
        catch (Exception ex)
        {
            throw new CryptoException(
                MessageFormat.format(
                    m_res.getString("NoLoadKeystore.exception.message"),
                    new Object[]{KeyStoreType.PKCS11}), ex);
        }

        return keyStore;
    }


    /**
     * Save a keystore to a file protected by a password.
     *
     * @param keyStore The keystore
     * @param fKeyStoreFile The file to save the keystore to
     * @param cPassword The password to protect the keystore with
     * @throws CryptoException Problem encountered saving the keystore
     * @throws FileNotFoundException If the keystore file exists but
     * is a directory rather than a regular file, does not exist but
     * cannot be created, or cannot be opened for any other reason
     * @throws IOException An I/O error occurred
     */
    public static void saveKeyStore(KeyStore keyStore, File fKeyStoreFile,
                                    char[] cPassword)
        throws CryptoException, IOException
    {
        FileOutputStream fos = new FileOutputStream(fKeyStoreFile);
        try
        {
            keyStore.store(fos, cPassword);
        }
        catch (IOException ex)
        {
            throw new CryptoException(
                m_res.getString("NoSaveKeystore.exception.message"), ex);
        }
        catch (GeneralSecurityException ex)
        {
            throw new CryptoException(
                m_res.getString("NoSaveKeystore.exception.message"), ex);
        }
        finally
        {
            fos.close();
        }
    }
}
