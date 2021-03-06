package fr.lteconsulting.hexa.classinfo.gwt.rebind;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.NotFoundException;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;

import fr.lteconsulting.hexa.classinfo.gwt.ReflectedClasses;

public class ClazzBundleGenerator extends Generator
{
	// Context and logger for code generation
	TreeLogger logger = null;
	GeneratorContext context = null;
	TypeOracle typeOracle = null;

	// asked type name
	String askedTypeName = null;

	// type info on the asked class
	JClassType askedType = null;

	//
	Set<JType> introspectedTypes;

	// package of the asked type
	String packageName = null;

	// generated class name
	String generatedClassName = null;

	@Override
	public String generate( TreeLogger logger, GeneratorContext context, String typeName ) throws UnableToCompleteException
	{
		this.logger = logger;
		this.context = context;
		this.askedTypeName = typeName;

		// get the "reflection" machine of GWT compiler
		typeOracle = context.getTypeOracle();
		try
		{
			// get classType and save instance variables
			askedType = typeOracle.getType( typeName );

			introspectedTypes = new HashSet<JType>();

			// list all return types of all methods
			JMethod[] methods = askedType.getMethods();
			for( int i = 0; i < methods.length; i++ )
			{
				ReflectedClasses classes = methods[i].getAnnotation( ReflectedClasses.class );
				if( classes == null || classes.classes() == null || classes.classes().length == 0 )
					continue;

				for( int c = 0; c < classes.classes().length; c++ )
				{
					JType classType = typeOracle.getType( classes.classes()[c].getName() );
					if( classType != null )
						introspectedTypes.add( classType );
				}
			}

			packageName = askedType.getPackage().getName();
			generatedClassName = askedType.getSimpleSourceName() + "ClazzBundleImpl";

			// Generate class source code
			generateClass();
		}
		catch( Exception e )
		{
			// record to logger that Map generation threw an exception
			logger.log( TreeLogger.ERROR, "ERROR when generating " + generatedClassName + " for " + typeName, e );
		}

		// return the fully qualifed name of the class generated
		return packageName + "." + generatedClassName;
	}

	private void generateClass()
	{
		// get print writer that receives the source code
		PrintWriter printWriter = null;

		printWriter = context.tryCreate( logger, packageName, generatedClassName );
		// print writer if null, source code has ALREADY been generated, return
		if( printWriter == null )
			return;

		// init composer, set class properties, create source writer
		ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory( packageName, generatedClassName );

		// output a class "typeName" + "Impl"
		// which extends the asked type
		composer.addImplementedInterface( askedType.getParameterizedQualifiedSourceName() );

		composer.addImport( "fr.lteconsulting.hexa.classinfo.Clazz" );
		composer.addImport( "fr.lteconsulting.hexa.classinfo.ClassInfo" );
		composer.addImport( "com.google.gwt.core.shared.GWT" );

		SourceWriter sourceWriter = composer.createSourceWriter( context, printWriter );

		// generate the List<String> getMethods(); method
		generateClass( sourceWriter );

		// close generated class
		sourceWriter.outdent();
		sourceWriter.println( "}" );

		// commit generated class
		context.commit( logger, printWriter );
	}

	private void generateClass( SourceWriter sourceWriter )
	{
		sourceWriter.println( "" );

		List<String> names = new ArrayList<String>();
		for( JType type : introspectedTypes )
		{
			String interfaceName = "Clazz_" + type.getQualifiedSourceName().replaceAll( "\\.", "_" );
			names.add( interfaceName );

			sourceWriter.println( "public interface " + interfaceName + " extends Clazz<" + type.getQualifiedSourceName() + "> {}" );
		}
		sourceWriter.println( "" );

		JMethod[] methods = askedType.getMethods();
		for( int m = 0; m < methods.length; m++ )
		{
			JMethod method = methods[m];
			ReflectedClasses classes = method.getAnnotation( ReflectedClasses.class );
			if( classes == null || classes.classes() == null || classes.classes().length == 0 )
				continue;

			sourceWriter.println( "public void " + method.getName() + "()" );
			sourceWriter.println( "{" );
			sourceWriter.indent();
			for( int c = 0; c < classes.classes().length; c++ )
			{
				JType type;
				try
				{
					type = typeOracle.getType( classes.classes()[c].getName() );
				}
				catch( NotFoundException e )
				{
					e.printStackTrace();
					continue;
				}

				String interfaceName = "Clazz_" + type.getQualifiedSourceName().replaceAll( "\\.", "_" );

				sourceWriter.println( "ClassInfo.RegisterClazz( (Clazz<?>) GWT.create( " + interfaceName + ".class ) );" );
			}
			sourceWriter.outdent();
			sourceWriter.println( "}" );
			sourceWriter.println( "" );
		}
	}
}
