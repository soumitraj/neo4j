/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api;

import java.util.Iterator;

import org.neo4j.kernel.api.StatementContext;
import org.neo4j.kernel.api.constraints.UniquenessConstraint;
import org.neo4j.kernel.api.exceptions.schema.AlreadyConstrainedException;
import org.neo4j.kernel.api.exceptions.schema.AlreadyIndexedException;
import org.neo4j.kernel.api.exceptions.schema.DropIndexFailureException;
import org.neo4j.kernel.api.exceptions.schema.IllegalTokenNameException;
import org.neo4j.kernel.api.exceptions.schema.NoSuchIndexException;
import org.neo4j.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.kernel.impl.api.index.IndexDescriptor;

import static org.neo4j.helpers.collection.IteratorUtil.loop;

public class DataIntegrityValidatingStatementContext extends CompositeStatementContext
{
    private final StatementContext delegate;

    public DataIntegrityValidatingStatementContext( StatementContext delegate )
    {
        super( delegate );
        this.delegate = delegate;
    }

    @Override
    public long getOrCreatePropertyKeyId( String propertyKey ) throws SchemaKernelException
    {
        // KISS - but refactor into a general purpose constraint checker later on
        if ( propertyKey == null )
        {
            throw new IllegalTokenNameException( null );
        }

        return delegate.getOrCreatePropertyKeyId( propertyKey );
    }

    @Override
    public long getOrCreateLabelId( String label ) throws SchemaKernelException
    {
        // KISS - but refactor into a general purpose constraint checker later on
        if ( label == null || label.length() == 0 )
        {
            throw new IllegalTokenNameException( label );
        }

        return delegate.getOrCreateLabelId( label );
    }

    @Override
    public IndexDescriptor addIndex( long labelId, long propertyKey )
            throws SchemaKernelException
    {
        checkIndexExistence( labelId, propertyKey );
        return delegate.addIndex( labelId, propertyKey );
    }

    @Override
    public IndexDescriptor addConstraintIndex( long labelId, long propertyKey )
            throws SchemaKernelException
    {
        checkIndexExistence( labelId, propertyKey );
        return delegate.addConstraintIndex( labelId, propertyKey );
    }

    private void checkIndexExistence( long labelId, long propertyKey ) throws SchemaKernelException
    {
        for ( IndexDescriptor descriptor : loop( getIndexes( labelId ) ) )
        {
            if ( descriptor.getPropertyKeyId() == propertyKey )
            {
                throw new AlreadyIndexedException( descriptor );
            }
        }
        for ( IndexDescriptor descriptor : loop( getConstraintIndexes( labelId ) ) )
        {
            if ( descriptor.getPropertyKeyId() == propertyKey )
            {
                throw new AlreadyConstrainedException(
                        new UniquenessConstraint( descriptor.getLabelId(), descriptor.getPropertyKeyId() ) );
            }
        }
    }

    @Override
    public void dropIndex( IndexDescriptor descriptor ) throws DropIndexFailureException
    {
        try
        {
            assertIndexExists( descriptor, getIndexes( descriptor.getLabelId() ) );
        }
        catch ( NoSuchIndexException e )
        {
            throw new DropIndexFailureException( descriptor, e );
        }
        delegate.dropIndex( descriptor );
    }

    @Override
    public void dropConstraintIndex( IndexDescriptor descriptor ) throws DropIndexFailureException
    {
        try
        {
            assertIndexExists( descriptor, getConstraintIndexes( descriptor.getLabelId() ) );
        }
        catch ( NoSuchIndexException e )
        {
            throw new DropIndexFailureException( descriptor, e );
        }
        delegate.dropConstraintIndex( descriptor );
    }

    @Override
    public UniquenessConstraint addUniquenessConstraint( long labelId, long propertyKey )
            throws SchemaKernelException, ConstraintCreationKernelException
    {
        Iterator<UniquenessConstraint> constraints = getConstraints( labelId, propertyKey );
        if ( constraints.hasNext() )
        {
            throw new AlreadyConstrainedException( constraints.next() );
        }

        return delegate.addUniquenessConstraint( labelId, propertyKey );
    }

    private void assertIndexExists( IndexDescriptor descriptor, Iterator<IndexDescriptor> indexes )
            throws NoSuchIndexException
    {
        for ( IndexDescriptor existing : loop( indexes ) )
        {
            if ( existing.getPropertyKeyId() == descriptor.getPropertyKeyId() )
            {
                return;
            }
        }
        throw new NoSuchIndexException( descriptor );
    }
}