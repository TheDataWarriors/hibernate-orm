/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.jaxb.hbm.transform;

/**
 * @author Steve Ebersole
 */
interface ColumnAndFormulaTarget {
	TargetColumnAdapter makeColumnAdapter(ColumnDefaults columnDefaults);

	void addColumn(TargetColumnAdapter column);

	void addFormula(String formula);
}
